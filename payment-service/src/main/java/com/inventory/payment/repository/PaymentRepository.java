package com.inventory.payment.repository;

import com.inventory.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    List<Payment> findByInvoiceNumber(String invoiceNumber);
    Optional<Payment> findTopByInvoiceNumberOrderByCreatedAtDesc(String invoiceNumber);
}
