package com.inventory.stockmovement.service;

import com.inventory.stockmovement.dto.response.ApiResponse;
import com.inventory.stockmovement.dto.response.StockMovementResponse;
import com.inventory.stockmovement.entity.StockMovement;
import com.inventory.stockmovement.enums.MovementType;
import com.inventory.stockmovement.feign.PaymentClient;
import com.inventory.stockmovement.feign.PurchaseOrderClient;
import feign.FeignException;
import com.inventory.stockmovement.mapper.StockMovementMapper;
import com.inventory.stockmovement.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository movementRepo;

    @Mock
    private StockMovementMapper movementMapper;

    @Mock
    private PurchaseOrderClient purchaseOrderClient;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private StockMovementService movementService;

    private StockMovement movement;
    private StockMovementResponse response;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
        movement = StockMovement.builder()
                .id(1L)
                .eventId("evt-123")
                .productId(100L)
                .warehouseId(200L)
                .movementType(MovementType.RECEIPT)
                .quantityDelta(50)
                .quantityAfter(150)
                .occurredAt(Instant.now())
                .build();

        response = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, null, null, null, null, Instant.now(), Instant.now()
        );
    }

    @Test
    void shouldGetAllMovements() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).eventId()).isEqualTo("evt-123");
        verify(movementRepo).findAll(pageable);
    }

    @Test
    void shouldGetMovementsByProduct() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findByProductId(100L, pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getByProduct(100L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(movementRepo).findByProductId(100L, pageable);
    }

    @Test
    void shouldGetMovementsByType() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findByMovementType(MovementType.RECEIPT, pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getByType(MovementType.RECEIPT, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(movementRepo).findByMovementType(MovementType.RECEIPT, pageable);
    }

    @Test
    void shouldGetMovementsByWarehouse() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findByWarehouseId(200L, pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getByWarehouse(200L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(movementRepo).findByWarehouseId(200L, pageable);
    }

    @Test
    void shouldGetMovementsByProductAndWarehouse() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findByProductIdAndWarehouseId(100L, 200L, pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getByProductAndWarehouse(100L, 200L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(movementRepo).findByProductIdAndWarehouseId(100L, 200L, pageable);
    }

    @Test
    void shouldGetMovementsByDateRange() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-03T00:00:00Z");
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findByDateRange(from, to, pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getByDateRange(from, to, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(movementRepo).findByDateRange(from, to, pageable);
    }

    @Test
    void shouldGetMovementsByProductAndDateRange() {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-05-03T00:00:00Z");
        when(movementRepo.findByProductIdAndDateRange(100L, from, to)).thenReturn(List.of(movement));
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        List<StockMovementResponse> result = movementService.getByProductAndDateRange(100L, from, to);

        assertThat(result).containsExactly(response);
    }

    @Test
    void shouldGetMovementsByReference() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findByReferenceIdAndReferenceType("REF-001", "PO", pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getByReference("REF-001", "PO", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(movementRepo).findByReferenceIdAndReferenceType("REF-001", "PO", pageable);
    }

    @Test
    void getAll_enrichesPurchaseOrderMovementWithTransactionId() {
        StockMovementResponse purchaseOrderResponse = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, "9", "PURCHASE_ORDER", null, null, Instant.now(), Instant.now()
        );
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(purchaseOrderResponse);
        when(purchaseOrderClient.getInvoiceByPurchaseOrderId(9L))
                .thenReturn(ApiResponse.success(new PurchaseOrderClient.InvoiceResponse(
                        1L, "INV-1", "PO-1", "APPROVED", 2L, "Acme", null,
                        "2026-05-02", "ISSUED", null, "2026-05-02T00:00:00Z"
                )));
        when(paymentClient.getLatestPaymentByInvoiceNumber("INV-1"))
                .thenReturn(ApiResponse.success(new PaymentClient.PaymentResponse(
                        1L, "TXN-77", "INV-1", null, "UPI", "PAID", null, "user",
                        Instant.parse("2026-05-02T00:00:00Z")
                )));

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result.getContent().get(0).transactionId()).isEqualTo("TXN-77");
    }

    @Test
    void getAll_leavesMovementUntouchedWhenReferenceTypeIsNotPurchaseOrder() {
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(response);

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result.getContent().get(0).transactionId()).isNull();
        verifyNoInteractions(purchaseOrderClient, paymentClient);
    }

    @Test
    void getAll_leavesMovementUntouchedWhenReferenceIdIsNotNumeric() {
        StockMovementResponse purchaseOrderResponse = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, "PO-9", "PURCHASE_ORDER", null, null, Instant.now(), Instant.now()
        );
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(purchaseOrderResponse);

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result.getContent().get(0).transactionId()).isNull();
    }

    @Test
    void getAll_leavesMovementUntouchedWhenInvoiceIsMissing() {
        StockMovementResponse purchaseOrderResponse = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, "9", "PURCHASE_ORDER", null, null, Instant.now(), Instant.now()
        );
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(purchaseOrderResponse);
        when(purchaseOrderClient.getInvoiceByPurchaseOrderId(9L)).thenReturn(ApiResponse.success(null));

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result.getContent().get(0).transactionId()).isNull();
        verifyNoInteractions(paymentClient);
    }

    @Test
    void getAll_leavesMovementUntouchedWhenPaymentIsMissing() {
        StockMovementResponse purchaseOrderResponse = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, "9", "PURCHASE_ORDER", null, null, Instant.now(), Instant.now()
        );
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(purchaseOrderResponse);
        when(purchaseOrderClient.getInvoiceByPurchaseOrderId(9L))
                .thenReturn(ApiResponse.success(new PurchaseOrderClient.InvoiceResponse(
                        1L, "INV-1", "PO-1", "APPROVED", 2L, "Acme", null,
                        "2026-05-02", "ISSUED", null, "2026-05-02T00:00:00Z"
                )));
        when(paymentClient.getLatestPaymentByInvoiceNumber("INV-1")).thenReturn(ApiResponse.success(null));

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result.getContent().get(0).transactionId()).isNull();
    }

    @Test
    void getAll_leavesMovementUntouchedWhenFeignFails() {
        StockMovementResponse purchaseOrderResponse = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, "9", "PURCHASE_ORDER", null, null, Instant.now(), Instant.now()
        );
        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(movementRepo.findAll(pageable)).thenReturn(page);
        when(movementMapper.toResponse(any(StockMovement.class))).thenReturn(purchaseOrderResponse);
        when(purchaseOrderClient.getInvoiceByPurchaseOrderId(9L)).thenThrow(mock(FeignException.class));

        Page<StockMovementResponse> result = movementService.getAll(pageable);

        assertThat(result.getContent().get(0).transactionId()).isNull();
    }
}
