package com.inventory.payment.service;

import com.inventory.payment.dto.PaymentRequest;
import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.entity.Payment;
import com.inventory.payment.entity.PaymentStatus;
import com.inventory.payment.feign.PurchaseOrderClient;
import com.inventory.payment.kafka.PaymentEvent;
import com.inventory.payment.kafka.PaymentEventPublisher;
import com.inventory.payment.mapper.PaymentMapper;
import com.inventory.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.inventory.payment.dto.RazorpayOrderResponse;
import com.inventory.payment.dto.RazorpayVerifyRequest;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentEventPublisher eventPublisher;
    private final PurchaseOrderClient purchaseOrderClient;

    @Value("${razorpay.key.id:rzp_test_YourKeyId}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:YourKeySecret}")
    private String razorpayKeySecret;

    @Transactional
    public RazorpayOrderResponse createRazorpayOrder(PaymentRequest request) {
        log.info("Creating Razorpay order for invoice: {}", request.invoiceNumber());
        validateInvoicePayable(request);
        if (isPlaceholderRazorpayConfig()) {
            throw new RuntimeException("Razorpay is not configured. Add valid razorpay.key.id and razorpay.key.secret to enable online payments.");
        }
        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            // Razorpay amount is in subunits (e.g., paise for INR)
            int amountInPaise = request.amount().multiply(new BigDecimal("100")).intValue();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + request.invoiceNumber());

            Order order = razorpay.orders.create(orderRequest);

            return new RazorpayOrderResponse(
                    order.get("id"),
                    request.amount(),
                    "INR"
            );
        } catch (Exception e) {
            log.error("Failed to create Razorpay order", e);
            throw new RuntimeException("Failed to initiate payment gateway: " + simplifyGatewayError(e.getMessage()));
        }
    }

    @Transactional
    public PaymentResponse verifyPayment(RazorpayVerifyRequest request, String username) {
        log.info("Verifying Razorpay payment for order: {}", request.razorpayOrderId());
        validateInvoicePayable(request.invoiceNumber(), request.amount());
        try {
            // Verify signature
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.razorpayOrderId());
            options.put("razorpay_payment_id", request.razorpayPaymentId());
            options.put("razorpay_signature", request.razorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            if (!isValid) {
                throw new RuntimeException("Payment signature verification failed");
            }

            // Create transaction ID and save to DB
            String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Payment payment = Payment.builder()
                    .transactionId(transactionId)
                    .invoiceNumber(request.invoiceNumber())
                    .amount(request.amount())
                    .paymentMethod("RAZORPAY")
                    .referenceNotes(request.referenceNotes())
                    .processedBy(username)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            payment = paymentRepository.save(payment);
            purchaseOrderClient.markInvoicePaid(payment.getInvoiceNumber(), payment.getTransactionId());

            // Publish event
            PaymentEvent event = PaymentEvent.builder()
                    .eventType("PAYMENT_PROCESSED")
                    .transactionId(payment.getTransactionId())
                    .invoiceNumber(payment.getInvoiceNumber())
                    .amount(payment.getAmount())
                    .status(payment.getStatus())
                    .triggeredBy(username)
                    .build();
            
            eventPublisher.publishEvent(event);

            return paymentMapper.toResponse(payment);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            throw new RuntimeException("Payment verification failed: " + e.getMessage());
        }
    }

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String username) {
        // Keeping this for non-Razorpay flows or direct payments
        log.info("Processing manual payment for invoice: {}", request.invoiceNumber());
        validateInvoicePayable(request);

        // Create transaction ID
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .transactionId(transactionId)
                .invoiceNumber(request.invoiceNumber())
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .referenceNotes(request.referenceNotes())
                .processedBy(username)
                .status(PaymentStatus.COMPLETED)
                .build();

        payment = paymentRepository.save(payment);
        purchaseOrderClient.markInvoicePaid(payment.getInvoiceNumber(), payment.getTransactionId());

        // Publish event
        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_PROCESSED")
                .transactionId(payment.getTransactionId())
                .invoiceNumber(payment.getInvoiceNumber())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .triggeredBy(username)
                .build();
        
        eventPublisher.publishEvent(event);

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable)
                .map(paymentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found for TXN: " + transactionId));
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getLatestPaymentByInvoiceNumber(String invoiceNumber) {
        Payment payment = paymentRepository.findTopByInvoiceNumberOrderByCreatedAtDesc(invoiceNumber)
                .orElseThrow(() -> new RuntimeException("Payment not found for invoice: " + invoiceNumber));
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse cancelPayment(String transactionId, String reason, String username) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found for TXN: " + transactionId));

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new RuntimeException("Payment has already been cancelled");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Only completed payments can be cancelled");
        }

        purchaseOrderClient.cancelOrderForPayment(
                payment.getInvoiceNumber(),
                payment.getTransactionId(),
                reason
        );

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setProcessedBy(username);
        payment.setReferenceNotes(appendAuditNote(payment.getReferenceNotes(),
                "Payment cancelled: " + reason.trim()));
        payment = paymentRepository.save(payment);

        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_CANCELLED")
                .transactionId(payment.getTransactionId())
                .invoiceNumber(payment.getInvoiceNumber())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .triggeredBy(username)
                .build();
        eventPublisher.publishEvent(event);

        return paymentMapper.toResponse(payment);
    }

    private boolean isPlaceholderRazorpayConfig() {
        return razorpayKeyId == null
                || razorpayKeySecret == null
                || razorpayKeyId.isBlank()
                || razorpayKeySecret.isBlank()
                || "rzp_test_YourKeyId".equals(razorpayKeyId)
                || "YourKeySecret".equals(razorpayKeySecret);
    }

    private String simplifyGatewayError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown gateway error";
        }
        if (message.contains("Authentication failed")) {
            return "Authentication failed. Check Razorpay API key and secret.";
        }
        return message;
    }

    private void validateInvoicePayable(PaymentRequest request) {
        validateInvoicePayable(request.invoiceNumber(), request.amount());
    }

    private void validateInvoicePayable(String invoiceNumber, BigDecimal amount) {
        PurchaseOrderClient.ApiResponse<PurchaseOrderClient.InvoiceResponse> response =
                purchaseOrderClient.getInvoiceByNumber(invoiceNumber);

        if (response == null || response.data() == null) {
            throw new RuntimeException("Unable to validate invoice before payment");
        }

        PurchaseOrderClient.InvoiceResponse invoice = response.data();

        if (!"APPROVED".equalsIgnoreCase(invoice.purchaseOrderStatus())
                && !"PARTIALLY_RECEIVED".equalsIgnoreCase(invoice.purchaseOrderStatus())
                && !"RECEIVED".equalsIgnoreCase(invoice.purchaseOrderStatus())) {
            throw new RuntimeException("Payment is allowed only after the purchase order is approved");
        }

        if ("PAID".equalsIgnoreCase(invoice.status())) {
            throw new RuntimeException("Invoice is already paid");
        }

        if ("CANCELLED".equalsIgnoreCase(invoice.status())) {
            throw new RuntimeException("Cancelled invoices cannot be paid");
        }

        if (invoice.amount() != null && amount != null && invoice.amount().compareTo(amount) != 0) {
            throw new RuntimeException("Payment amount does not match the invoice amount");
        }
    }

    private String appendAuditNote(String existingNotes, String auditNote) {
        if (existingNotes == null || existingNotes.isBlank()) {
            return auditNote;
        }
        if (existingNotes.contains(auditNote)) {
            return existingNotes;
        }
        return existingNotes + " | " + auditNote;
    }
}
