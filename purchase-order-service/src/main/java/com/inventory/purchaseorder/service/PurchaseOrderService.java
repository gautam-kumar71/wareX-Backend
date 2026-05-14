package com.inventory.purchaseorder.service;

import com.inventory.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import com.inventory.purchaseorder.dto.request.ReceiveStockRequest;
import com.inventory.purchaseorder.dto.response.PurchaseOrderResponse;
import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.entity.PurchaseOrderLine;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.event.OrderEvent;
import com.inventory.purchaseorder.exception.PurchaseOrderNotFoundException;
import com.inventory.purchaseorder.exception.SupplierValidationException;
import com.inventory.purchaseorder.exception.WarehouseServiceUnavailableException;
import com.inventory.purchaseorder.feign.SupplierClient;
import com.inventory.purchaseorder.feign.WarehouseClient;
import com.inventory.purchaseorder.event.OrderEventPublisher;
import com.inventory.purchaseorder.mapper.PurchaseOrderMapper;
import com.inventory.purchaseorder.repository.PurchaseOrderLineRepository;
import com.inventory.purchaseorder.repository.PurchaseOrderRepository;
import com.inventory.purchaseorder.repository.InvoiceRepository;
import com.inventory.purchaseorder.statemachine.PurchaseOrderStateMachine;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {
    private static final List<PurchaseOrderStatus> DEACTIVATION_BLOCKING_STATUSES = List.of(
            PurchaseOrderStatus.DRAFT,
            PurchaseOrderStatus.SUBMITTED,
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );
    private static final List<Invoice.InvoiceStatus> DEACTIVATION_BLOCKING_INVOICE_STATUSES = List.of(
            Invoice.InvoiceStatus.PENDING,
            Invoice.InvoiceStatus.APPROVED,
            Invoice.InvoiceStatus.OVERDUE
    );

    private final PurchaseOrderRepository     poRepo;
    private final PurchaseOrderLineRepository lineRepo;
    private final InvoiceRepository           invoiceRepo;
    private final PurchaseOrderMapper         mapper;
    private final PurchaseOrderStateMachine   stateMachine;
    private final OrderEventPublisher         eventPublisher;
    private final WarehouseClient             warehouseClient;
    private final SupplierClient              supplierClient;
    private final InvoiceService               invoiceService;

    // ─────────────────────────────────────────────────────────────────────────
    // Create — DRAFT status
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest req,
                                                     String createdByUserId) {
        // 1. Validate supplier exists and is active via Feign (single call)
        SupplierClient.SupplierResponse supplier = validateAndGetSupplier(req.supplierId());

        // 2. Validate warehouse exists and is active via Feign
        WarehouseClient.WarehouseResponse warehouse = validateWarehouse(req.warehouseId());
        validateWarehouseCapacityForOrder(req, warehouse);

        // 3. Build order
        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(generateOrderNumber())
                .supplierId(req.supplierId())
                .supplierName(supplier.name())
                .warehouseId(req.warehouseId())
                .status(PurchaseOrderStatus.DRAFT)
                .notes(req.notes())
                .expectedDate(req.expectedDate())
                .createdBy(createdByUserId)
                .build();

        // 4. Build and attach lines
        req.lines().forEach(lineReq -> {
            PurchaseOrderLine line = PurchaseOrderLine.builder()
                    .productId(lineReq.productId())
                    .productName(lineReq.productName())
                    .productSku(lineReq.productSku())
                    .orderedQty(lineReq.orderedQty())
                    .unitPrice(lineReq.unitPrice())
                    .build();
            line.recalculateLineTotal();
            order.addLine(line);
        });

        order.recalculateTotal();
        PurchaseOrder saved = poRepo.save(order);

        // 5. Publish ORDER_CREATED event
        publishEvent(saved, "ORDER_CREATED", createdByUserId, null);

        log.info("Purchase order created: orderNumber={}, supplier={}, total={}",
                saved.getOrderNumber(), saved.getSupplierId(), saved.getTotalAmount());

        return mapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(Long id) {
        return poRepo.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getByOrderNumber(String orderNumber) {
        return poRepo.findByOrderNumber(orderNumber)
                .map(mapper::toResponse)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(orderNumber));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getAll(Pageable pageable) {
        return poRepo.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getByStatus(PurchaseOrderStatus status,
                                                   Pageable pageable) {
        return poRepo.findByStatus(status, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getBySupplier(Long supplierId, Pageable pageable) {
        return poRepo.findBySupplierId(supplierId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getByWarehouse(Long warehouseId, Pageable pageable) {
        return poRepo.findByWarehouseId(warehouseId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplierDeactivationCheckResponse getSupplierDeactivationCheck(Long supplierId) {
        List<PurchaseOrder> blockingOrders =
                poRepo.findBySupplierIdAndStatusIn(supplierId, DEACTIVATION_BLOCKING_STATUSES);
        List<Invoice> blockingInvoices =
                invoiceRepo.findBySupplierIdAndStatusIn(supplierId, DEACTIVATION_BLOCKING_INVOICE_STATUSES);

        if (blockingOrders.isEmpty() && blockingInvoices.isEmpty()) {
            return new SupplierDeactivationCheckResponse(true, 0, List.of(), List.of(), 0, List.of(), List.of(), null);
        }

        List<String> blockingStatuses = blockingOrders.stream()
                .map(PurchaseOrder::getStatus)
                .distinct()
                .map(Enum::name)
                .sorted()
                .toList();

        String blockingOrderNumbers = blockingOrders.stream()
                .map(PurchaseOrder::getOrderNumber)
                .limit(3)
                .collect(Collectors.joining(", "));

        List<String> blockingInvoiceStatuses = blockingInvoices.stream()
                .map(Invoice::getStatus)
                .distinct()
                .map(Enum::name)
                .sorted()
                .toList();

        String blockingInvoiceNumbers = blockingInvoices.stream()
                .map(Invoice::getInvoiceNumber)
                .limit(3)
                .collect(Collectors.joining(", "));

        List<String> reasons = new java.util.ArrayList<>();

        if (!blockingOrders.isEmpty()) {
            String orderReason = "active purchase order(s)";
            if (!blockingStatuses.isEmpty()) {
                orderReason += " in " + String.join(", ", blockingStatuses);
            }
            if (!blockingOrderNumbers.isBlank()) {
                orderReason += " [" + blockingOrderNumbers + (blockingOrders.size() > 3 ? "..." : "") + "]";
            }
            reasons.add(blockingOrders.size() + " " + orderReason);
        }

        if (!blockingInvoices.isEmpty()) {
            String invoiceReason = "unsettled invoice(s)";
            if (!blockingInvoiceStatuses.isEmpty()) {
                invoiceReason += " in " + String.join(", ", blockingInvoiceStatuses);
            }
            if (!blockingInvoiceNumbers.isBlank()) {
                invoiceReason += " [" + blockingInvoiceNumbers + (blockingInvoices.size() > 3 ? "..." : "") + "]";
            }
            reasons.add(blockingInvoices.size() + " " + invoiceReason);
        }

        String message = "Supplier cannot be deactivated because it has " + String.join(" and ", reasons) + ".";

        return new SupplierDeactivationCheckResponse(
                false,
                blockingOrders.size(),
                blockingStatuses,
                blockingOrders.stream()
                        .map(PurchaseOrder::getOrderNumber)
                        .limit(5)
                        .toList(),
                blockingInvoices.size(),
                blockingInvoiceStatuses,
                blockingInvoices.stream()
                        .map(Invoice::getInvoiceNumber)
                        .limit(5)
                        .toList(),
                message
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Submit — DRAFT → SUBMITTED
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse submitOrder(Long id, String userId) {
        PurchaseOrder order = findOrThrow(id);

        stateMachine.validateTransition(order.getStatus(), PurchaseOrderStatus.SUBMITTED);

        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        PurchaseOrder saved = poRepo.save(order);

        publishEvent(saved, "ORDER_SUBMITTED", userId, null);
        log.info("PO submitted: orderNumber={}", saved.getOrderNumber());

        return mapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Approve — SUBMITTED → APPROVED
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse approveOrder(Long id, String approverUserId) {
        PurchaseOrder order = findOrThrow(id);

        stateMachine.validateTransition(order.getStatus(), PurchaseOrderStatus.APPROVED);

        order.setStatus(PurchaseOrderStatus.APPROVED);
        order.setApprovedBy(approverUserId);
        PurchaseOrder saved = poRepo.save(order);

        // Auto-generate invoice when approved
        invoiceService.generateInvoiceFromOrder(saved);

        publishEvent(saved, "ORDER_APPROVED", approverUserId, null);
        log.info("PO approved: orderNumber={}, approvedBy={}", saved.getOrderNumber(), approverUserId);

        return mapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Receive stock — APPROVED → PARTIALLY_RECEIVED | RECEIVED
    //
    // This is the most complex operation:
    //   1. Validate the PO is in APPROVED or PARTIALLY_RECEIVED state
    //   2. Validate the line exists and quantity doesn't exceed remaining
    //   3. Update received_qty on the line
    //   4. Call Warehouse Service via Feign to physically add stock
    //   5. Determine new status (partial vs full)
    //   6. Publish Kafka event
    //
    // Edge cases handled:
    //   - Receiving more than ordered → rejected
    //   - Warehouse Service down → WarehouseServiceUnavailableException (503)
    //   - Line not on this PO → 404
    //   - PO not in receivable state → 409 (state machine rejects)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse receiveStock(Long id, ReceiveStockRequest req,
                                              String userId) {
        PurchaseOrder order = findOrThrow(id);

        if (!invoiceService.isInvoicePaidForPurchaseOrder(order.getId())) {
            throw new IllegalStateException(
                    "Stock can be received only after the invoice payment is completed");
        }

        // Only APPROVED and PARTIALLY_RECEIVED orders can receive stock
        if (order.getStatus() != PurchaseOrderStatus.APPROVED &&
                order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            stateMachine.validateTransition(order.getStatus(),
                    PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }

        // Find the line for this product
        PurchaseOrderLine line = order.getLines().stream()
                .filter(l -> l.getProductId().equals(req.productId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Product %d is not on purchase order %s"
                                .formatted(req.productId(), order.getOrderNumber())));

        // Validate: cannot receive more than remaining quantity
        int remaining = line.getRemainingQty();
        if (req.receivedQty() > remaining) {
            throw new IllegalArgumentException(
                    "Cannot receive %d units. Only %d units remaining on this line (product %d)."
                            .formatted(req.receivedQty(), remaining, req.productId()));
        }

        // Update received quantity on the line
        line.setReceivedQty(line.getReceivedQty() + req.receivedQty());

        // Call Warehouse Service to physically add stock — this is a SYNCHRONOUS call.
        // If it fails, the @Transactional rolls back the line update too.
        // This is intentional: stock integrity requires both to succeed together.
        try {
            warehouseClient.receiveStock(
                    order.getWarehouseId(),
                    req.productId(),
                    req.receivedQty(),
                    order.getId().toString()
            );
        } catch (FeignException.BadRequest ex) {
            throw new IllegalArgumentException(extractFeignMessage(ex));
        } catch (FeignException.Conflict ex) {
            throw new IllegalStateException(extractFeignMessage(ex));
        } catch (FeignException.NotFound ex) {
            throw new EntityNotFoundException(extractFeignMessage(ex));
        } catch (FeignException.Forbidden ex) {
            throw new IllegalArgumentException(extractFeignMessage(ex));
        } catch (FeignException ex) {
            throw new WarehouseServiceUnavailableException(extractFeignMessage(ex));
        } catch (WarehouseServiceUnavailableException ex) {
            // Let the transaction roll back — the line update is NOT committed
            throw ex;
        }

        // Determine new PO status based on whether all lines are now received
        PurchaseOrderStatus newStatus =
                stateMachine.resolveReceiptStatus(order.isFullyReceived());
        order.setStatus(newStatus);

        if (newStatus == PurchaseOrderStatus.RECEIVED) {
            order.setReceivedAt(Instant.now());
        }

        PurchaseOrder saved = poRepo.save(order);

        // Build received items list for the Kafka event
        List<OrderEvent.ReceivedLineItem> receivedItems = List.of(
                OrderEvent.ReceivedLineItem.builder()
                        .productId(req.productId())
                        .productSku(line.getProductSku())
                        .receivedQty(req.receivedQty())
                        .build()
        );

        String eventType = newStatus == PurchaseOrderStatus.RECEIVED
                ? "ORDER_RECEIVED"
                : "ORDER_PARTIALLY_RECEIVED";

        publishEvent(saved, eventType, userId, receivedItems);

        log.info("Stock received for PO {}: product={}, qty={}, newStatus={}",
                order.getOrderNumber(), req.productId(), req.receivedQty(), newStatus);

        return mapper.toResponse(saved);
    }

    @Transactional
    public void cancelOrderForPayment(String invoiceNumber, String transactionId,
                                      String reason, String userId) {
        Invoice invoice = invoiceService.getInvoiceByNumber(invoiceNumber);
        PurchaseOrder order = invoice.getPurchaseOrder();

        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            invoiceService.cancelPaidInvoice(invoiceNumber, transactionId, reason);
            return;
        }

        reverseReceivedStockIfNecessary(order);

        order.getLines().forEach(line -> line.setReceivedQty(0));
        order.setReceivedAt(null);
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelledBy(userId);
        order.setCancelReason(buildPaymentCancellationReason(transactionId, reason));
        PurchaseOrder saved = poRepo.save(order);

        invoiceService.cancelPaidInvoice(invoiceNumber, transactionId, reason);
        publishEvent(saved, "ORDER_CANCELLED", userId, null);

        log.info("PO cancelled after payment cancellation: orderNumber={}, invoiceNumber={}, transactionId={}",
                saved.getOrderNumber(), invoiceNumber, transactionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancel — any non-terminal state → CANCELLED
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrderResponse cancelOrder(Long id, String reason, String userId) {
        PurchaseOrder order = findOrThrow(id);

        stateMachine.validateTransition(order.getStatus(), PurchaseOrderStatus.CANCELLED);

        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelledBy(userId);
        order.setCancelReason(reason);
        PurchaseOrder saved = poRepo.save(order);
        invoiceService.cancelInvoiceForPurchaseOrder(saved.getId(), reason);

        publishEvent(saved, "ORDER_CANCELLED", userId, null);
        log.info("PO cancelled: orderNumber={}, by={}, reason={}",
                saved.getOrderNumber(), userId, reason);

        return mapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private PurchaseOrder findOrThrow(Long id) {
        return poRepo.findById(id)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(id));
    }

    /**
     * Generates a unique order number: PO-YYYYMMDD-XXXXX
     * e.g. PO-20250901-A3F9C
     */
    private String generateOrderNumber() {
        String datePart = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniquePart = UUID.randomUUID().toString()
                .substring(0, 5).toUpperCase();
        String candidate = "PO-" + datePart + "-" + uniquePart;

        // Retry on collision (astronomically rare but handled correctly)
        int attempts = 0;
        while (poRepo.existsByOrderNumber(candidate) && attempts < 5) {
            uniquePart = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
            candidate = "PO-" + datePart + "-" + uniquePart;
            attempts++;
        }
        return candidate;
    }

    private SupplierClient.SupplierResponse validateAndGetSupplier(Long supplierId) {
        SupplierClient.ApiResponse<SupplierClient.SupplierResponse> response = supplierClient.getSupplierById(supplierId);
        if (response == null || response.data() == null) {
            throw new SupplierValidationException(
                    "Supplier Service is unavailable. Cannot validate supplier.");
        }
        SupplierClient.SupplierResponse supplier = response.data();
        if (!supplier.active()) {
            throw new SupplierValidationException(
                    "Supplier %d (%s) is inactive and cannot receive purchase orders."
                            .formatted(supplierId, supplier.name()));
        }
        return supplier;
    }

    private WarehouseClient.WarehouseResponse validateWarehouse(Long warehouseId) {
        WarehouseClient.ApiResponse<WarehouseClient.WarehouseResponse> response = warehouseClient.getWarehouseById(warehouseId);
        if (response == null || response.data() == null) {
            throw new WarehouseServiceUnavailableException(
                    "Warehouse Service is unavailable. Cannot validate warehouse.");
        }
        WarehouseClient.WarehouseResponse warehouse = response.data();
        if (!warehouse.active()) {
            throw new IllegalArgumentException(
                    "Warehouse %d is inactive. Please select an active warehouse."
                            .formatted(warehouseId));
        }
        return warehouse;
    }

    private void validateWarehouseCapacityForOrder(CreatePurchaseOrderRequest req,
                                                   WarehouseClient.WarehouseResponse warehouse) {
        if (warehouse.totalStorageCapacity() == null || warehouse.totalStorageCapacity() <= 0) {
            throw new IllegalArgumentException(
                    "Warehouse '%s' does not have a tracked maximum capacity configured. Set a positive warehouse capacity before creating purchase orders for it."
                            .formatted(warehouse.name())
            );
        }

        int requestedUnits = req.lines().stream()
                .mapToInt(line -> line.orderedQty() == null ? 0 : line.orderedQty())
                .sum();
        int usedCapacity = warehouse.currentCapacityUtilization() == null
                ? 0
                : warehouse.currentCapacityUtilization();
        int remainingCapacity = Math.max(0, warehouse.totalStorageCapacity() - usedCapacity);

        if (requestedUnits > remainingCapacity) {
            throw new IllegalArgumentException(
                    "Warehouse '%s' only has %d tracked unit(s) free, but this order needs %d. Reduce the order quantity, choose another warehouse, or increase warehouse capacity."
                            .formatted(warehouse.name(), remainingCapacity, requestedUnits)
            );
        }
    }

    private void publishEvent(PurchaseOrder order, String eventType,
                              String triggeredBy,
                              List<OrderEvent.ReceivedLineItem> receivedItems) {
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .purchaseOrderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .supplierId(order.getSupplierId())
                .warehouseId(order.getWarehouseId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .receivedItems(receivedItems)
                .triggeredBy(triggeredBy)
                .occurredAt(Instant.now())
                .build();

        eventPublisher.fireEvent(event);
    }

    private void reverseReceivedStockIfNecessary(PurchaseOrder order) {
        for (PurchaseOrderLine line : order.getLines()) {
            if (line.getReceivedQty() <= 0) {
                continue;
            }

            try {
                warehouseClient.reverseReceivedStock(
                        order.getWarehouseId(),
                        line.getProductId(),
                        line.getReceivedQty(),
                        order.getId().toString()
                );
            } catch (FeignException.BadRequest ex) {
                throw new IllegalArgumentException(extractFeignMessage(ex));
            } catch (FeignException.Conflict ex) {
                throw new IllegalStateException(extractFeignMessage(ex));
            } catch (FeignException.NotFound ex) {
                throw new EntityNotFoundException(extractFeignMessage(ex));
            } catch (FeignException ex) {
                throw new WarehouseServiceUnavailableException(extractFeignMessage(ex));
            }
        }
    }

    private String buildPaymentCancellationReason(String transactionId, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (transactionId == null || transactionId.isBlank()) {
            return normalizedReason.isBlank()
                    ? "Order cancelled after payment cancellation"
                    : "Order cancelled after payment cancellation: " + normalizedReason;
        }
        if (normalizedReason.isBlank()) {
            return "Order cancelled after payment cancellation (" + transactionId + ")";
        }
        return "Order cancelled after payment cancellation (" + transactionId + "): " + normalizedReason;
    }

    private String extractFeignMessage(FeignException ex) {
        String content = ex.contentUTF8();
        if (content != null && !content.isBlank()) {
            int messageIndex = content.indexOf("\"message\":\"");
            if (messageIndex >= 0) {
                int start = messageIndex + 11;
                int end = content.indexOf('"', start);
                if (end > start) {
                    return content.substring(start, end);
                }
            }
        }
        return "Warehouse receipt failed. Please retry.";
    }

    public record SupplierDeactivationCheckResponse(
            boolean canDeactivate,
            long blockingOrderCount,
            List<String> blockingStatuses,
            List<String> blockingOrderNumbers,
            long blockingInvoiceCount,
            List<String> blockingInvoiceStatuses,
            List<String> blockingInvoiceNumbers,
            String message
    ) {}
}
