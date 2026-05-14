package com.inventory.purchaseorder.controller;

import com.inventory.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import com.inventory.purchaseorder.dto.request.PurchaseOrderLineRequest;
import com.inventory.purchaseorder.dto.request.ReceiveStockRequest;
import com.inventory.purchaseorder.dto.response.PurchaseOrderLineResponse;
import com.inventory.purchaseorder.dto.response.PurchaseOrderResponse;
import com.inventory.purchaseorder.entity.Invoice;
import com.inventory.purchaseorder.enums.PurchaseOrderStatus;
import com.inventory.purchaseorder.service.PurchaseOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PurchaseOrderControllerTest {

    private final PurchaseOrderService service = mock(PurchaseOrderService.class);
    private final PurchaseOrderController controller = new PurchaseOrderController(service);

    @Test
    void create_returnsCreatedResponse() {
        given(service.createPurchaseOrder(any(), org.mockito.ArgumentMatchers.eq("u1"))).willReturn(response(1L));

        var result = controller.create(request(), "u1");

        assertThat(result.getStatusCodeValue()).isEqualTo(201);
        assertThat(result.getBody().message()).isEqualTo("Purchase order created successfully");
    }

    @Test
    void getAll_routesToCorrectServiceMethod() {
        var pageable = PageRequest.of(0, 20);
        given(service.getAll(pageable)).willReturn(new PageImpl<>(List.of(response(1L))));
        given(service.getByStatus(PurchaseOrderStatus.APPROVED, pageable)).willReturn(new PageImpl<>(List.of(response(2L))));
        given(service.getByWarehouse(5L, pageable)).willReturn(new PageImpl<>(List.of(response(3L))));

        assertThat(controller.getAll(null, null, pageable).getBody().data().getContent()).hasSize(1);
        assertThat(controller.getAll(PurchaseOrderStatus.APPROVED, null, pageable).getBody().data().getContent().get(0).id()).isEqualTo(2L);
        assertThat(controller.getAll(null, 5L, pageable).getBody().data().getContent().get(0).id()).isEqualTo(3L);
    }

    @Test
    void readAndTransitionEndpoints_delegateAndWrap() {
        var pageable = PageRequest.of(0, 20);
        given(service.getById(1L)).willReturn(response(1L));
        given(service.getByOrderNumber("PO-1")).willReturn(response(1L));
        given(service.getBySupplier(9L, pageable)).willReturn(new PageImpl<>(List.of(response(1L))));
        given(service.getSupplierDeactivationCheck(9L)).willReturn(
                new PurchaseOrderService.SupplierDeactivationCheckResponse(
                        false,
                        2L,
                        List.of("APPROVED"),
                        List.of("PO-1"),
                        1L,
                        List.of("PENDING"),
                        List.of("INV-1"),
                        "blocked"
                )
        );
        given(service.submitOrder(1L, "u1")).willReturn(response(1L));
        given(service.approveOrder(1L, "u1")).willReturn(response(1L));
        given(service.receiveStock(1L, new ReceiveStockRequest(10L, 2), "u1")).willReturn(response(1L));
        given(service.cancelOrder(1L, "reason", "u1")).willReturn(response(1L));

        assertThat(controller.getById(1L).getBody().data().id()).isEqualTo(1L);
        assertThat(controller.getByOrderNumber("PO-1").getBody().data().orderNumber()).isEqualTo("PO-1");
        assertThat(controller.getBySupplier(9L, pageable).getBody().data().getContent()).hasSize(1);
        assertThat(controller.getSupplierDeactivationCheck(9L).getBody().data().blockingOrderCount()).isEqualTo(2L);
        assertThat(controller.submit(1L, "u1").getBody().message()).contains("submitted");
        assertThat(controller.approve(1L, "u1").getBody().message()).contains("approved");
        assertThat(controller.receiveStock(1L, new ReceiveStockRequest(10L, 2), "u1").getBody().message()).contains("Stock received");
        assertThat(controller.cancel(1L, "reason", "u1").getBody().message()).contains("cancelled");
    }

    private CreatePurchaseOrderRequest request() {
        return new CreatePurchaseOrderRequest(
                5L,
                8L,
                List.of(new PurchaseOrderLineRequest(10L, "Widget", "SKU-1", 3, BigDecimal.TEN)),
                "notes",
                LocalDate.parse("2026-01-10")
        );
    }

    private PurchaseOrderResponse response(Long id) {
        return new PurchaseOrderResponse(
                id,
                "PO-" + id,
                5L,
                "Supplier",
                8L,
                PurchaseOrderStatus.DRAFT,
                BigDecimal.TEN,
                "notes",
                "u1",
                null,
                null,
                null,
                LocalDate.parse("2026-01-10"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                List.of(new PurchaseOrderLineResponse(1L, 10L, "Widget", "SKU-1", 3, 0, 3, BigDecimal.TEN, new BigDecimal("30"), false))
        );
    }
}
