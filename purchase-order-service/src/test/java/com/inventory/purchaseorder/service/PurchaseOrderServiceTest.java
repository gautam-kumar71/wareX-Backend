package com.inventory.purchaseorder.service;

import com.inventory.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import com.inventory.purchaseorder.dto.request.PurchaseOrderLineRequest;
import com.inventory.purchaseorder.dto.request.ReceiveStockRequest;
import com.inventory.purchaseorder.dto.response.PurchaseOrderResponse;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.entity.PurchaseOrder;
import com.inventory.purchaseorder.entity.PurchaseOrderLine;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.event.OrderEvent;
import com.inventory.purchaseorder.event.OrderEventPublisher;
import com.inventory.purchaseorder.exception.PurchaseOrderNotFoundException;
import com.inventory.purchaseorder.exception.SupplierValidationException;
import com.inventory.purchaseorder.exception.WarehouseServiceUnavailableException;
import com.inventory.purchaseorder.feign.SupplierClient;
import com.inventory.purchaseorder.feign.WarehouseClient;
import com.inventory.purchaseorder.mapper.PurchaseOrderMapper;
import com.inventory.purchaseorder.repository.InvoiceRepository;
import com.inventory.purchaseorder.repository.PurchaseOrderLineRepository;
import com.inventory.purchaseorder.repository.PurchaseOrderRepository;
import com.inventory.purchaseorder.statemachine.PurchaseOrderStateMachine;
import feign.FeignException;
import feign.Request;
import feign.Response;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository poRepo;
    @Mock
    private PurchaseOrderLineRepository lineRepo;
    @Mock
    private InvoiceRepository invoiceRepo;
    @Mock
    private PurchaseOrderMapper mapper;
    @Mock
    private PurchaseOrderStateMachine stateMachine;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private WarehouseClient warehouseClient;
    @Mock
    private SupplierClient supplierClient;
    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private PurchaseOrderService service;

    @Test
    void createPurchaseOrder_validatesDependenciesAndPublishesEvent() {
        CreatePurchaseOrderRequest req = request();
        given(supplierClient.getSupplierById(5L)).willReturn(new SupplierClient.ApiResponse<>(new SupplierClient.SupplierResponse(5L, "Supplier", "c@test.com", true)));
        given(warehouseClient.getWarehouseById(8L)).willReturn(new WarehouseClient.ApiResponse<>(warehouse(true, 100, 20)));
        given(poRepo.existsByOrderNumber(any())).willReturn(false);
        given(poRepo.save(any(PurchaseOrder.class))).willAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            po.setId(1L);
            return po;
        });
        PurchaseOrderResponse response = response(1L);
        given(mapper.toResponse(any(PurchaseOrder.class))).willReturn(response);

        assertThat(service.createPurchaseOrder(req, "u1")).isSameAs(response);

        verify(poRepo).save(any(PurchaseOrder.class));
        verify(eventPublisher).fireEvent(any(OrderEvent.class));
    }

    @Test
    void createPurchaseOrder_rejectsUnavailableOrInactiveSupplierAndWarehouse() {
        CreatePurchaseOrderRequest req = request();
        given(supplierClient.getSupplierById(5L)).willReturn(null);
        assertThatThrownBy(() -> service.createPurchaseOrder(req, "u1"))
                .isInstanceOf(SupplierValidationException.class);

        given(supplierClient.getSupplierById(5L)).willReturn(new SupplierClient.ApiResponse<>(new SupplierClient.SupplierResponse(5L, "Supplier", "c@test.com", false)));
        assertThatThrownBy(() -> service.createPurchaseOrder(req, "u1"))
                .isInstanceOf(SupplierValidationException.class)
                .hasMessageContaining("inactive");

        given(supplierClient.getSupplierById(5L)).willReturn(new SupplierClient.ApiResponse<>(new SupplierClient.SupplierResponse(5L, "Supplier", "c@test.com", true)));
        given(warehouseClient.getWarehouseById(8L)).willReturn(null);
        assertThatThrownBy(() -> service.createPurchaseOrder(req, "u1"))
                .isInstanceOf(WarehouseServiceUnavailableException.class);

        given(warehouseClient.getWarehouseById(8L)).willReturn(new WarehouseClient.ApiResponse<>(warehouse(false, 100, 20)));
        assertThatThrownBy(() -> service.createPurchaseOrder(req, "u1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void createPurchaseOrder_rejectsCapacityProblems() {
        CreatePurchaseOrderRequest req = request();
        given(supplierClient.getSupplierById(5L)).willReturn(new SupplierClient.ApiResponse<>(new SupplierClient.SupplierResponse(5L, "Supplier", "c@test.com", true)));
        given(warehouseClient.getWarehouseById(8L)).willReturn(new WarehouseClient.ApiResponse<>(warehouse(true, null, 20)));

        assertThatThrownBy(() -> service.createPurchaseOrder(req, "u1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tracked maximum capacity");

        given(warehouseClient.getWarehouseById(8L)).willReturn(new WarehouseClient.ApiResponse<>(warehouse(true, 10, 9)));
        assertThatThrownBy(() -> service.createPurchaseOrder(req, "u1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only has");
    }

    @Test
    void readMethods_delegateOrThrow() {
        PurchaseOrder order = order(PurchaseOrderStatus.DRAFT);
        var pageable = PageRequest.of(0, 20);
        PurchaseOrderResponse response = response(1L);

        given(poRepo.findById(1L)).willReturn(Optional.of(order));
        given(poRepo.findByOrderNumber("PO-1")).willReturn(Optional.of(order));
        given(poRepo.findAll(pageable)).willReturn(new PageImpl<>(List.of(order)));
        given(poRepo.findByStatus(PurchaseOrderStatus.APPROVED, pageable)).willReturn(new PageImpl<>(List.of(order)));
        given(poRepo.findBySupplierId(5L, pageable)).willReturn(new PageImpl<>(List.of(order)));
        given(poRepo.findByWarehouseId(8L, pageable)).willReturn(new PageImpl<>(List.of(order)));
        given(mapper.toResponse(order)).willReturn(response);

        assertThat(service.getById(1L)).isSameAs(response);
        assertThat(service.getByOrderNumber("PO-1")).isSameAs(response);
        assertThat(service.getAll(pageable).getContent()).containsExactly(response);
        assertThat(service.getByStatus(PurchaseOrderStatus.APPROVED, pageable).getContent()).containsExactly(response);
        assertThat(service.getBySupplier(5L, pageable).getContent()).containsExactly(response);
        assertThat(service.getByWarehouse(8L, pageable).getContent()).containsExactly(response);

        given(poRepo.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(PurchaseOrderNotFoundException.class);
    }

    @Test
    void getSupplierDeactivationCheck_handlesClearAndBlockedCases() {
        given(poRepo.findBySupplierIdAndStatusIn(any(), any())).willReturn(List.of());
        given(invoiceRepo.findBySupplierIdAndStatusIn(any(), any())).willReturn(List.of());

        assertThat(service.getSupplierDeactivationCheck(5L).canDeactivate()).isTrue();

        PurchaseOrder po = order(PurchaseOrderStatus.APPROVED);
        Invoice invoice = Invoice.builder().invoiceNumber("INV-1").status(Invoice.InvoiceStatus.PENDING).build();
        given(poRepo.findBySupplierIdAndStatusIn(any(), any())).willReturn(List.of(po));
        given(invoiceRepo.findBySupplierIdAndStatusIn(any(), any())).willReturn(List.of(invoice));

        var response = service.getSupplierDeactivationCheck(5L);
        assertThat(response.canDeactivate()).isFalse();
        assertThat(response.message()).contains("cannot be deactivated");
    }

    @Test
    void submitApproveAndCancel_updateStateAndPublishEvents() {
        PurchaseOrder order = order(PurchaseOrderStatus.DRAFT);
        PurchaseOrder submitted = order(PurchaseOrderStatus.SUBMITTED);
        PurchaseOrder approved = order(PurchaseOrderStatus.APPROVED);
        PurchaseOrder cancelled = order(PurchaseOrderStatus.CANCELLED);
        PurchaseOrderResponse response = response(1L);

        given(poRepo.findById(1L)).willReturn(Optional.of(order), Optional.of(submitted), Optional.of(approved));
        given(poRepo.save(any(PurchaseOrder.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(mapper.toResponse(any(PurchaseOrder.class))).willReturn(response);

        assertThat(service.submitOrder(1L, "u1")).isSameAs(response);
        verify(stateMachine).validateTransition(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.SUBMITTED);

        submitted.setStatus(PurchaseOrderStatus.SUBMITTED);
        assertThat(service.approveOrder(1L, "u2")).isSameAs(response);
        verify(invoiceService).generateInvoiceFromOrder(any(PurchaseOrder.class));

        approved.setStatus(PurchaseOrderStatus.APPROVED);
        assertThat(service.cancelOrder(1L, "reason", "u3")).isSameAs(response);
        verify(invoiceService).cancelInvoiceForPurchaseOrder(1L, "reason");
    }

    @Test
    void receiveStock_handlesHappyPathAndStateResolution() {
        PurchaseOrder order = order(PurchaseOrderStatus.APPROVED);
        PurchaseOrderLine line = order.getLines().get(0);
        line.setOrderedQty(5);
        line.setReceivedQty(1);
        PurchaseOrderResponse response = response(1L);

        given(poRepo.findById(1L)).willReturn(Optional.of(order));
        given(poRepo.save(any(PurchaseOrder.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(mapper.toResponse(any(PurchaseOrder.class))).willReturn(response);
        given(stateMachine.resolveReceiptStatus(false)).willReturn(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        given(invoiceService.isInvoicePaidForPurchaseOrder(1L)).willReturn(true);

        assertThat(service.receiveStock(1L, new ReceiveStockRequest(10L, 2), "u1")).isSameAs(response);
        assertThat(line.getReceivedQty()).isEqualTo(3);
        verify(warehouseClient).receiveStock(8L, 10L, 2, "1");
    }

    @Test
    void receiveStock_handlesValidationAndFeignFailures() {
        PurchaseOrder order = order(PurchaseOrderStatus.RECEIVED);
        given(poRepo.findById(1L)).willReturn(Optional.of(order));
        given(invoiceService.isInvoicePaidForPurchaseOrder(1L)).willReturn(true);
        doNothing().when(stateMachine).validateTransition(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

        assertThatThrownBy(() -> service.receiveStock(1L, new ReceiveStockRequest(99L, 1), "u1"))
                .isInstanceOf(EntityNotFoundException.class);

        PurchaseOrder tooMany = order(2L, PurchaseOrderStatus.APPROVED);
        tooMany.getLines().get(0).setOrderedQty(2);
        tooMany.getLines().get(0).setReceivedQty(1);
        given(poRepo.findById(2L)).willReturn(Optional.of(tooMany));
        given(invoiceService.isInvoicePaidForPurchaseOrder(2L)).willReturn(true);

        assertThatThrownBy(() -> service.receiveStock(2L, new ReceiveStockRequest(10L, 5), "u1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only 1 units remaining");

        PurchaseOrder badRequestOrder = order(3L, PurchaseOrderStatus.APPROVED);
        badRequestOrder.getLines().get(0).setOrderedQty(2);
        badRequestOrder.getLines().get(0).setReceivedQty(0);
        given(poRepo.findById(3L)).willReturn(Optional.of(badRequestOrder));
        given(invoiceService.isInvoicePaidForPurchaseOrder(3L)).willReturn(true);
        org.mockito.Mockito.doThrow(feign(400, "{\"message\":\"bad request\"}")).when(warehouseClient).receiveStock(8L, 10L, 1, "3");
        assertThatThrownBy(() -> service.receiveStock(3L, new ReceiveStockRequest(10L, 1), "u1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad request");

        PurchaseOrder conflictOrder = order(4L, PurchaseOrderStatus.APPROVED);
        conflictOrder.getLines().get(0).setOrderedQty(2);
        given(poRepo.findById(4L)).willReturn(Optional.of(conflictOrder));
        given(invoiceService.isInvoicePaidForPurchaseOrder(4L)).willReturn(true);
        org.mockito.Mockito.doThrow(feign(409, "{\"message\":\"conflict\"}")).when(warehouseClient).receiveStock(8L, 10L, 1, "4");
        assertThatThrownBy(() -> service.receiveStock(4L, new ReceiveStockRequest(10L, 1), "u1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflict");

        PurchaseOrder missingOrder = order(5L, PurchaseOrderStatus.APPROVED);
        missingOrder.getLines().get(0).setOrderedQty(2);
        given(poRepo.findById(5L)).willReturn(Optional.of(missingOrder));
        given(invoiceService.isInvoicePaidForPurchaseOrder(5L)).willReturn(true);
        org.mockito.Mockito.doThrow(feign(404, "{\"message\":\"missing\"}")).when(warehouseClient).receiveStock(8L, 10L, 1, "5");
        assertThatThrownBy(() -> service.receiveStock(5L, new ReceiveStockRequest(10L, 1), "u1"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("missing");

        PurchaseOrder unavailableOrder = order(6L, PurchaseOrderStatus.APPROVED);
        unavailableOrder.getLines().get(0).setOrderedQty(2);
        given(poRepo.findById(6L)).willReturn(Optional.of(unavailableOrder));
        given(invoiceService.isInvoicePaidForPurchaseOrder(6L)).willReturn(true);
        org.mockito.Mockito.doThrow(feign(500, "")).when(warehouseClient).receiveStock(8L, 10L, 1, "6");
        assertThatThrownBy(() -> service.receiveStock(6L, new ReceiveStockRequest(10L, 1), "u1"))
                .isInstanceOf(WarehouseServiceUnavailableException.class);
    }

    @Test
    void receiveStock_rejectsUnpaidInvoice() {
        PurchaseOrder order = order(7L, PurchaseOrderStatus.APPROVED);
        given(poRepo.findById(7L)).willReturn(Optional.of(order));
        given(invoiceService.isInvoicePaidForPurchaseOrder(7L)).willReturn(false);

        assertThatThrownBy(() -> service.receiveStock(7L, new ReceiveStockRequest(10L, 1), "u1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only after the invoice payment");
    }

    @Test
    void cancelOrderForPayment_reversesReceivedStockAndCancelsInvoice() {
        PurchaseOrder order = order(PurchaseOrderStatus.RECEIVED);
        order.getLines().get(0).setReceivedQty(3);
        Invoice invoice = Invoice.builder()
                .invoiceNumber("INV-1")
                .purchaseOrder(order)
                .status(Invoice.InvoiceStatus.PAID)
                .build();

        given(invoiceService.getInvoiceByNumber("INV-1")).willReturn(invoice);
        given(poRepo.save(any(PurchaseOrder.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.cancelOrderForPayment("INV-1", "TXN-1", "duplicate", "u9");

        verify(warehouseClient).reverseReceivedStock(8L, 10L, 3, "1");
        verify(invoiceService).cancelPaidInvoice("INV-1", "TXN-1", "duplicate");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        assertThat(order.getLines().get(0).getReceivedQty()).isZero();
    }

    private CreatePurchaseOrderRequest request() {
        return new CreatePurchaseOrderRequest(
                5L,
                8L,
                List.of(
                        new PurchaseOrderLineRequest(10L, "Widget", "SKU-1", 3, BigDecimal.TEN),
                        new PurchaseOrderLineRequest(11L, "Gadget", "SKU-2", 2, BigDecimal.ONE)
                ),
                "notes",
                LocalDate.parse("2026-01-10")
        );
    }

    private WarehouseClient.WarehouseResponse warehouse(boolean active, Integer totalCapacity, Integer utilization) {
        return new WarehouseClient.WarehouseResponse(
                8L, "Main", "Plot", "Pune", "India", totalCapacity, utilization,
                utilization == null || totalCapacity == null || totalCapacity == 0 ? null : Math.round((utilization * 100f) / totalCapacity),
                0, 0, "Manager", "999", null, null, null, null, active
        );
    }

    private PurchaseOrder order(PurchaseOrderStatus status) {
        return order(1L, status);
    }

    private PurchaseOrder order(Long id, PurchaseOrderStatus status) {
        PurchaseOrderLine line = PurchaseOrderLine.builder()
                .id(1L)
                .productId(10L)
                .productName("Widget")
                .productSku("SKU-1")
                .orderedQty(5)
                .receivedQty(0)
                .unitPrice(BigDecimal.TEN)
                .lineTotal(new BigDecimal("50"))
                .build();
        return PurchaseOrder.builder()
                .id(id)
                .orderNumber("PO-" + id)
                .supplierId(5L)
                .supplierName("Supplier")
                .warehouseId(8L)
                .status(status)
                .totalAmount(new BigDecimal("50"))
                .createdBy("u1")
                .lines(new java.util.ArrayList<>(List.of(line)))
                .build();
    }

    private PurchaseOrderResponse response(Long id) {
        return new PurchaseOrderResponse(
                id, "PO-" + id, 5L, "Supplier", 8L, PurchaseOrderStatus.DRAFT,
                BigDecimal.TEN, "notes", "u1", null, null, null,
                LocalDate.parse("2026-01-10"), null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"),
                List.of()
        );
    }

    private FeignException feign(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/test", Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("reason")
                .request(request)
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("receiveStock", response);
    }
}
