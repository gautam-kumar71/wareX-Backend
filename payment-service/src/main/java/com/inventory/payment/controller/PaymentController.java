package com.inventory.payment.controller;

import com.inventory.payment.dto.PaymentRequest;
import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.dto.response.ApiResponse;
import com.inventory.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/razorpay/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    public ResponseEntity<ApiResponse<com.inventory.payment.dto.RazorpayOrderResponse>> createRazorpayOrder(
            @Valid @RequestBody PaymentRequest request) {
        com.inventory.payment.dto.RazorpayOrderResponse response = paymentService.createRazorpayOrder(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Razorpay order created"));
    }

    @PostMapping("/razorpay/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyRazorpayPayment(
            @Valid @RequestBody com.inventory.payment.dto.RazorpayVerifyRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        PaymentResponse response = paymentService.verifyPayment(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Razorpay payment verified successfully"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        
        PaymentResponse response = paymentService.processPayment(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment processed successfully"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getAllPayments(@PageableDefault(size = 50) Pageable pageable) {
        Page<PaymentResponse> page = paymentService.getAllPayments(pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Payments retrieved successfully"));
    }

    @GetMapping("/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByTxn(@PathVariable String transactionId) {
        PaymentResponse response = paymentService.getPaymentByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment retrieved successfully"));
    }

    @GetMapping("/invoice/{invoiceNumber}/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PaymentResponse>> getLatestPaymentByInvoice(
            @PathVariable String invoiceNumber) {
        PaymentResponse response = paymentService.getLatestPaymentByInvoiceNumber(invoiceNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Latest payment retrieved successfully"));
    }

    @PostMapping("/{transactionId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'PURCHASE_OFFICER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @PathVariable String transactionId,
            @RequestParam String reason,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        PaymentResponse response = paymentService.cancelPayment(transactionId, reason, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment cancelled successfully"));
    }
}
