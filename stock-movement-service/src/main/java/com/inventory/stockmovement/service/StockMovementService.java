package com.inventory.stockmovement.service;

import com.inventory.stockmovement.dto.response.StockMovementResponse;
import com.inventory.stockmovement.enums.MovementType;
import com.inventory.stockmovement.feign.PaymentClient;
import com.inventory.stockmovement.feign.PurchaseOrderClient;
import com.inventory.stockmovement.mapper.StockMovementMapper;
import com.inventory.stockmovement.repository.StockMovementRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository movementRepo;
    private final StockMovementMapper     movementMapper;
    private final PurchaseOrderClient     purchaseOrderClient;
    private final PaymentClient           paymentClient;

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getByProduct(Long productId, Pageable pageable) {
        return movementRepo.findByProductId(productId, pageable)
                .map(movementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getByWarehouse(Long warehouseId, Pageable pageable) {
        return movementRepo.findByWarehouseId(warehouseId, pageable)
                .map(movementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getByProductAndWarehouse(Long productId,
                                                                Long warehouseId,
                                                                Pageable pageable) {
        return movementRepo.findByProductIdAndWarehouseId(productId, warehouseId, pageable)
                .map(movementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getByType(MovementType type, Pageable pageable) {
        return movementRepo.findByMovementType(type, pageable)
                .map(movementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getByDateRange(Instant from, Instant to,
                                                      Pageable pageable) {
        return movementRepo.findByDateRange(from, to, pageable)
                .map(movementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> getByProductAndDateRange(Long productId,
                                                                Instant from,
                                                                Instant to) {
        return movementRepo.findByProductIdAndDateRange(productId, from, to)
                .stream()
                .map(movementMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getByReference(String referenceId,
                                                      String referenceType,
                                                      Pageable pageable) {
        return movementRepo.findByReferenceIdAndReferenceType(
                        referenceId, referenceType, pageable)
                .map(movementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getAll(Pageable pageable) {
        Page<StockMovementResponse> page = movementRepo.findAll(pageable).map(movementMapper::toResponse);
        return enrichWithTransactionIds(page);
    }

    private Page<StockMovementResponse> enrichWithTransactionIds(Page<StockMovementResponse> page) {
        List<StockMovementResponse> enriched = page.getContent().stream()
                .map(this::enrichWithTransactionId)
                .toList();
        return new PageImpl<>(enriched, page.getPageable(), page.getTotalElements());
    }

    private StockMovementResponse enrichWithTransactionId(StockMovementResponse movement) {
        if (!"PURCHASE_ORDER".equalsIgnoreCase(movement.referenceType()) || movement.referenceId() == null) {
            return movement;
        }

        try {
            Long purchaseOrderId = Long.valueOf(movement.referenceId());
            PurchaseOrderClient.InvoiceResponse invoice =
                    purchaseOrderClient.getInvoiceByPurchaseOrderId(purchaseOrderId).getData();
            if (invoice == null || invoice.invoiceNumber() == null || invoice.invoiceNumber().isBlank()) {
                return movement;
            }

            PaymentClient.PaymentResponse payment =
                    paymentClient.getLatestPaymentByInvoiceNumber(invoice.invoiceNumber()).getData();
            if (payment == null || payment.transactionId() == null || payment.transactionId().isBlank()) {
                return movement;
            }

            return new StockMovementResponse(
                    movement.id(),
                    movement.eventId(),
                    movement.productId(),
                    movement.productName(),
                    movement.warehouseId(),
                    movement.warehouseName(),
                    movement.movementType(),
                    movement.quantityDelta(),
                    movement.quantityAfter(),
                    movement.referenceId(),
                    movement.referenceType(),
                    payment.transactionId(),
                    movement.notes(),
                    movement.occurredAt(),
                    movement.recordedAt()
            );
        } catch (NumberFormatException | FeignException ex) {
            return movement;
        }
    }
}
