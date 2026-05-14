package com.inventory.payment.dto;

import java.math.BigDecimal;

public record RazorpayOrderResponse(
        String razorpayOrderId,
        BigDecimal amount,
        String currency
) {}
