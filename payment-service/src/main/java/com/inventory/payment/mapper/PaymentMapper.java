package com.inventory.payment.mapper;

import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);
}
