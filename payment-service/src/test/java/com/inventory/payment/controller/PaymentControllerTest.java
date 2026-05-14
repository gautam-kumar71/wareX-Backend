package com.inventory.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.payment.dto.PaymentRequest;
import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.dto.RazorpayOrderResponse;
import com.inventory.payment.dto.RazorpayVerifyRequest;
import com.inventory.payment.dto.response.ApiResponse;
import com.inventory.payment.entity.PaymentStatus;
import com.inventory.payment.exception.GlobalExceptionHandler;
import com.inventory.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void createAndVerifyEndpoints_returnWrappedResponses() throws Exception {
        when(paymentService.createRazorpayOrder(any(PaymentRequest.class)))
                .thenReturn(new RazorpayOrderResponse("order_123", new BigDecimal("250.00"), "INR"));
        when(paymentService.verifyPayment(any(RazorpayVerifyRequest.class), any(String.class)))
                .thenReturn(paymentResponse("TXN-VERIFY", "INV-1"));

        mockMvc.perform(post("/api/v1/payments/razorpay/order")
                        .contentType("application/json")
                        .content("""
                                {"invoiceNumber":"INV-1","amount":250.00,"paymentMethod":"RAZORPAY","referenceNotes":"notes"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Razorpay order created"))
                .andExpect(jsonPath("$.data.razorpayOrderId").value("order_123"));

        mockMvc.perform(post("/api/v1/payments/razorpay/verify")
                        .contentType("application/json")
                        .header("X-User-Id", "u-1")
                        .content("""
                                {"razorpayPaymentId":"pay_1","razorpayOrderId":"order_1","razorpaySignature":"sig_1","invoiceNumber":"INV-1","amount":250.00,"referenceNotes":"ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Razorpay payment verified successfully"))
                .andExpect(jsonPath("$.data.transactionId").value("TXN-VERIFY"));

        verify(paymentService).verifyPayment(any(RazorpayVerifyRequest.class), org.mockito.Mockito.eq("u-1"));
    }

    @Test
    void processAndReadEndpoints_returnExpectedPayloads() throws Exception {
        PaymentResponse response = paymentResponse("TXN-200", "INV-2");
        when(paymentService.processPayment(any(PaymentRequest.class), any(String.class))).thenReturn(response);
        when(paymentService.getPaymentByTransactionId("TXN-200")).thenReturn(response);
        when(paymentService.getLatestPaymentByInvoiceNumber("INV-2")).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .header("X-User-Id", "system-user")
                        .content("""
                                {"invoiceNumber":"INV-2","amount":99.99,"paymentMethod":"CARD","referenceNotes":"manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment processed successfully"))
                .andExpect(jsonPath("$.data.transactionId").value("TXN-200"));

        mockMvc.perform(get("/api/v1/payments/TXN-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value("TXN-200"));

        mockMvc.perform(get("/api/v1/payments/invoice/INV-2/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-2"));
    }

    @Test
    void getAllPayments_returnsWrappedPageDirectly() {
        PaymentResponse response = paymentResponse("TXN-PAGE", "INV-PAGE");
        PageRequest pageable = PageRequest.of(0, 50);
        when(paymentService.getAllPayments(pageable)).thenReturn(new PageImpl<>(List.of(response), pageable, 1));

        ResponseEntity<ApiResponse<org.springframework.data.domain.Page<PaymentResponse>>> entity =
                new PaymentController(paymentService).getAllPayments(pageable);

        assert entity.getStatusCode().is2xxSuccessful();
        assert entity.getBody() != null;
        assert entity.getBody().data().getContent().get(0).invoiceNumber().equals("INV-PAGE");
    }

    @Test
    void invalidRequest_isHandledByGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType("application/json")
                        .content("""
                                {"invoiceNumber":"","amount":0,"paymentMethod":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    private PaymentResponse paymentResponse(String transactionId, String invoiceNumber) {
        return new PaymentResponse(
                1L,
                transactionId,
                invoiceNumber,
                new BigDecimal("99.99"),
                "CARD",
                PaymentStatus.COMPLETED,
                "notes",
                "system-user",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
