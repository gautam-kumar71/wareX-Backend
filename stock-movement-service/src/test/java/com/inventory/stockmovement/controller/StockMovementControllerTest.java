package com.inventory.stockmovement.controller;

import com.inventory.stockmovement.dto.response.StockMovementResponse;
import com.inventory.stockmovement.enums.MovementType;
import com.inventory.stockmovement.service.StockMovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StockMovementController.class)
@AutoConfigureMockMvc(addFilters = false) // Disables Spring Security for unit testing the controller
class StockMovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockMovementService movementService;

    private StockMovementResponse response;

    @BeforeEach
    void setUp() {
        response = new StockMovementResponse(
                1L, "evt-123", 100L, "Test Product", 200L, "Test Warehouse", MovementType.RECEIPT,
                50, 150, "REF-001", "PO", null, "Test note", Instant.now(), Instant.now()
        );
    }

    @Test
    void shouldGetAllStockMovements() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getAll(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-123"))
                .andExpect(jsonPath("$.data.content[0].quantityDelta").value(50));
    }

    @Test
    void shouldGetStockMovementsByProduct() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getByProduct(eq(100L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements/product/100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].productId").value(100));
    }

    @Test
    void shouldGetStockMovementsByWarehouse() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getByWarehouse(eq(200L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements/warehouse/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].warehouseId").value(200));
    }

    @Test
    void shouldGetStockMovementsByProductAndWarehouse() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getByProductAndWarehouse(eq(100L), eq(200L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements/product/100/warehouse/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productId").value(100))
                .andExpect(jsonPath("$.data.content[0].warehouseId").value(200));
    }

    @Test
    void shouldGetStockMovementsByType() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getByType(eq(MovementType.RECEIPT), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements/type/RECEIPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].movementType").value("RECEIPT"));
    }

    @Test
    void shouldGetStockMovementsByDateRange() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getByDateRange(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements/date-range")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-05-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-123"));
    }

    @Test
    void shouldGetProductHistory() throws Exception {
        when(movementService.getByProductAndDateRange(eq(100L), any(), any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/stock-movements/product/100/history")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-05-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value(100));
    }

    @Test
    void shouldGetStockMovementsByReference() throws Exception {
        Page<StockMovementResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        when(movementService.getByReference(eq("REF-001"), eq("PO"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/stock-movements/reference")
                        .param("referenceId", "REF-001")
                        .param("referenceType", "PO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].referenceId").value("REF-001"));
    }
}
