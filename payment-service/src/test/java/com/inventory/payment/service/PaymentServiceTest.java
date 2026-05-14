package com.inventory.payment.service;

import com.inventory.payment.dto.PaymentRequest;
import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.dto.RazorpayOrderResponse;
import com.inventory.payment.dto.RazorpayVerifyRequest;
import com.inventory.payment.entity.Payment;
import com.inventory.payment.entity.PaymentStatus;
import com.inventory.payment.feign.PurchaseOrderClient;
import com.inventory.payment.kafka.PaymentEvent;
import com.inventory.payment.kafka.PaymentEventPublisher;
import com.inventory.payment.mapper.PaymentMapper;
import com.inventory.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PaymentEventPublisher eventPublisher;
    @Mock
    private PurchaseOrderClient purchaseOrderClient;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPayment_savesMarksInvoicePaidPublishesEventAndReturnsResponse() {
        PaymentRequest request = new PaymentRequest("INV-1", new BigDecimal("250.00"), "UPI", "notes");
        Payment saved = payment("TXN-ABCD1234", "INV-1", new BigDecimal("250.00"), "UPI");
        PaymentResponse response = response(saved);

        when(purchaseOrderClient.getInvoiceByNumber("INV-1")).thenReturn(payableInvoice("INV-1", "APPROVED", "PENDING", new BigDecimal("250.00")));
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(paymentMapper.toResponse(saved)).thenReturn(response);

        assertThat(paymentService.processPayment(request, "alice")).isSameAs(response);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getProcessedBy()).isEqualTo("alice");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(purchaseOrderClient).markInvoicePaid("INV-1", "TXN-ABCD1234");

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getInvoiceNumber()).isEqualTo("INV-1");
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("PAYMENT_PROCESSED");
        assertThat(eventCaptor.getValue().getTriggeredBy()).isEqualTo("alice");
    }

    @Test
    void verifyPayment_withValidSignature_savesMarksInvoicePaidPublishesEventAndReturnsResponse() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "secret-value-1234567890");

        RazorpayVerifyRequest request = new RazorpayVerifyRequest(
                "pay_1",
                "order_1",
                "sig_1",
                "INV-2",
                new BigDecimal("99.99"),
                "verified"
        );
        Payment saved = payment("TXN-VERIFIED", "INV-2", new BigDecimal("99.99"), "RAZORPAY");
        PaymentResponse response = response(saved);

        when(purchaseOrderClient.getInvoiceByNumber("INV-2")).thenReturn(payableInvoice("INV-2", "APPROVED", "PENDING", new BigDecimal("99.99")));
        when(paymentRepository.save(any(Payment.class))).thenReturn(saved);
        when(paymentMapper.toResponse(saved)).thenReturn(response);

        try (MockedStatic<com.razorpay.Utils> utils = mockStatic(com.razorpay.Utils.class)) {
            utils.when(() -> com.razorpay.Utils.verifyPaymentSignature(any(org.json.JSONObject.class), any(String.class)))
                    .thenReturn(true);

            assertThat(paymentService.verifyPayment(request, "bob")).isSameAs(response);
        }

        verify(purchaseOrderClient).markInvoicePaid("INV-2", "TXN-VERIFIED");
        verify(eventPublisher).publishEvent(any(PaymentEvent.class));
    }

    @Test
    void verifyPayment_withInvalidSignature_throwsWrappedError() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "secret-value-1234567890");

        RazorpayVerifyRequest request = new RazorpayVerifyRequest(
                "pay_bad",
                "order_bad",
                "sig_bad",
                "INV-3",
                new BigDecimal("55.00"),
                null
        );

        when(purchaseOrderClient.getInvoiceByNumber("INV-3")).thenReturn(payableInvoice("INV-3", "APPROVED", "PENDING", new BigDecimal("55.00")));

        try (MockedStatic<com.razorpay.Utils> utils = mockStatic(com.razorpay.Utils.class)) {
            utils.when(() -> com.razorpay.Utils.verifyPaymentSignature(any(org.json.JSONObject.class), any(String.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> paymentService.verifyPayment(request, "bob"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Payment verification failed")
                    .hasMessageContaining("Payment signature verification failed");
        }
    }

    @Test
    void createRazorpayOrder_rejectsPlaceholderConfigEvenForValidInvoice() {
        PaymentRequest request = new PaymentRequest("INV-4", new BigDecimal("10.00"), "RAZORPAY", null);

        when(purchaseOrderClient.getInvoiceByNumber("INV-4")).thenReturn(payableInvoice("INV-4", "APPROVED", "PENDING", new BigDecimal("10.00")));
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_YourKeyId");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "YourKeySecret");

        assertThatThrownBy(() -> paymentService.createRazorpayOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Razorpay is not configured");
    }

    @Test
    void helperMethods_coverPlaceholderAndGatewayMessageCases() {
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_YourKeyId");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "YourKeySecret");
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(paymentService, "isPlaceholderRazorpayConfig")).isTrue();

        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_live_realistic_key_id");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "this-is-a-long-plain-secret-for-tests");
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(paymentService, "isPlaceholderRazorpayConfig")).isFalse();

        assertThat((String) ReflectionTestUtils.invokeMethod(paymentService, "simplifyGatewayError", "Authentication failed while creating order"))
                .isEqualTo("Authentication failed. Check Razorpay API key and secret.");
        assertThat((String) ReflectionTestUtils.invokeMethod(paymentService, "simplifyGatewayError", ""))
                .isEqualTo("Unknown gateway error");
        assertThat((String) ReflectionTestUtils.invokeMethod(paymentService, "simplifyGatewayError", "other failure"))
                .isEqualTo("other failure");
    }

    @Test
    void invoiceValidationRejectsMissingInvalidOrAlreadyPaidInvoices() {
        PaymentRequest request = new PaymentRequest("INV-5", new BigDecimal("75.00"), "CARD", null);

        when(purchaseOrderClient.getInvoiceByNumber("INV-5")).thenReturn(null);
        assertThatThrownBy(() -> paymentService.processPayment(request, "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unable to validate invoice");

        when(purchaseOrderClient.getInvoiceByNumber("INV-5")).thenReturn(payableInvoice("INV-5", "SUBMITTED", "PENDING", new BigDecimal("75.00")));
        assertThatThrownBy(() -> paymentService.processPayment(request, "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("purchase order is approved");

        when(purchaseOrderClient.getInvoiceByNumber("INV-5")).thenReturn(payableInvoice("INV-5", "RECEIVED", "PAID", new BigDecimal("75.00")));
        assertThatThrownBy(() -> paymentService.processPayment(request, "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already paid");

        when(purchaseOrderClient.getInvoiceByNumber("INV-5")).thenReturn(payableInvoice("INV-5", "RECEIVED", "CANCELLED", new BigDecimal("75.00")));
        assertThatThrownBy(() -> paymentService.processPayment(request, "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cancelled invoices");

        when(purchaseOrderClient.getInvoiceByNumber("INV-5")).thenReturn(payableInvoice("INV-5", "RECEIVED", "PENDING", new BigDecimal("80.00")));
        assertThatThrownBy(() -> paymentService.processPayment(request, "alice"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void readMethods_returnMappedValuesOrThrow() {
        Payment payment = payment("TXN-READ-1", "INV-6", new BigDecimal("11.00"), "CARD");
        PaymentResponse response = response(payment);

        when(paymentRepository.findAll(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(payment)));
        when(paymentRepository.findByTransactionId("TXN-READ-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findTopByInvoiceNumberOrderByCreatedAtDesc("INV-6")).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(response);

        assertThat(paymentService.getAllPayments(PageRequest.of(0, 20)).getContent()).containsExactly(response);
        assertThat(paymentService.getPaymentByTransactionId("TXN-READ-1")).isSameAs(response);
        assertThat(paymentService.getLatestPaymentByInvoiceNumber("INV-6")).isSameAs(response);

        when(paymentRepository.findByTransactionId("MISSING")).thenReturn(Optional.empty());
        when(paymentRepository.findTopByInvoiceNumberOrderByCreatedAtDesc("INV-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByTransactionId("MISSING"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found for TXN");
        assertThatThrownBy(() -> paymentService.getLatestPaymentByInvoiceNumber("INV-404"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found for invoice");
    }

    @Test
    void cancelPayment_marksPaymentCancelledAndCancelsOrder() {
        Payment payment = payment("TXN-CANCEL-1", "INV-7", new BigDecimal("45.00"), "CARD");
        payment.setStatus(PaymentStatus.COMPLETED);
        PaymentResponse response = new PaymentResponse(
                payment.getId(),
                payment.getTransactionId(),
                payment.getInvoiceNumber(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                PaymentStatus.CANCELLED,
                "Payment cancelled: duplicate",
                "alice",
                payment.getCreatedAt()
        );

        when(paymentRepository.findByTransactionId("TXN-CANCEL-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(response);

        assertThat(paymentService.cancelPayment("TXN-CANCEL-1", "duplicate", "alice")).isSameAs(response);

        verify(purchaseOrderClient).cancelOrderForPayment("INV-7", "TXN-CANCEL-1", "duplicate");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(eventPublisher).publishEvent(any(PaymentEvent.class));
    }

    private PurchaseOrderClient.ApiResponse<PurchaseOrderClient.InvoiceResponse> payableInvoice(
            String invoiceNumber,
            String poStatus,
            String invoiceStatus,
            BigDecimal amount
    ) {
        return new PurchaseOrderClient.ApiResponse<>(
                Instant.now().toString(),
                200,
                "ok",
                new PurchaseOrderClient.InvoiceResponse(
                        1L,
                        invoiceNumber,
                        "PO-1",
                        poStatus,
                        5L,
                        "Supplier",
                        amount,
                        "2026-05-01",
                        invoiceStatus,
                        "notes",
                        Instant.now().toString()
                )
        );
    }

    private Payment payment(String transactionId, String invoiceNumber, BigDecimal amount, String method) {
        return Payment.builder()
                .id(1L)
                .transactionId(transactionId)
                .invoiceNumber(invoiceNumber)
                .amount(amount)
                .paymentMethod(method)
                .referenceNotes("notes")
                .processedBy("tester")
                .status(PaymentStatus.COMPLETED)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private PaymentResponse response(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getTransactionId(),
                payment.getInvoiceNumber(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getReferenceNotes(),
                payment.getProcessedBy(),
                payment.getCreatedAt()
        );
    }
}
