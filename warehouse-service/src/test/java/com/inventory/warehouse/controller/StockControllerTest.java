package com.inventory.warehouse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.warehouse.config.SecurityConfig;
import com.inventory.warehouse.dto.request.AdjustStockRequest;
import com.inventory.warehouse.dto.request.BulkThresholdUpdateRequest;
import com.inventory.warehouse.dto.request.TransferStockRequest;
import com.inventory.warehouse.dto.response.StockLevelResponse;
import com.inventory.warehouse.enums.AdjustmentReason;
import com.inventory.warehouse.exception.GlobalExceptionHandler;
import com.inventory.warehouse.exception.InsufficientStockException;
import com.inventory.warehouse.exception.StockLevelNotFoundException;
import com.inventory.warehouse.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StockController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class StockControllerTest {

    @Autowired MockMvc       mvc;
    @Autowired ObjectMapper  objectMapper;
    @MockBean  StockService  stockService;

    private StockLevelResponse mockStockResponse() {
        return new StockLevelResponse(
                1L, 1L, "Main Warehouse", 100L,
                "Product 100", "SKU-100",
                50, 5, 45, 10, 200, false, false, Instant.now());
    }

    // ─── GET stock level ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void getStockLevel_exists_returns200() throws Exception {
        given(stockService.getStockLevel(1L, 100L)).willReturn(mockStockResponse());

        mvc.perform(get("/api/v1/stock/warehouses/1/products/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(50))
                .andExpect(jsonPath("$.data.availableQty").value(45))
                .andExpect(jsonPath("$.data.warehouseName").value("Main Warehouse"));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void getStockLevel_notFound_returns404() throws Exception {
        given(stockService.getStockLevel(1L, 999L))
                .willThrow(new StockLevelNotFoundException(1L, 999L));

        mvc.perform(get("/api/v1/stock/warehouses/1/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("No stock record")));
    }

    @Test
    void getStockLevel_noToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/stock/warehouses/1/products/100"))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET by warehouse ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void getByWarehouse_returns200WithList() throws Exception {
        given(stockService.getStockByWarehouse(1L, null, false, false, "updatedAt", "desc"))
                .willReturn(List.of(mockStockResponse()));

        mvc.perform(get("/api/v1/stock/warehouses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productId").value(100));
    }

    // ─── GET low stock ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void getLowStock_returns200() throws Exception {
        StockLevelResponse lowStockItem = new StockLevelResponse(
                2L, 1L, "Main Warehouse", 200L,
                "Product 200", "SKU-200",
                5, 0, 5, 10, null, true, false, Instant.now());
        given(stockService.getLowStockItems(null, null, "availableQty", "asc")).willReturn(List.of(lowStockItem));

        mvc.perform(get("/api/v1/stock/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lowStock").value(true));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void getByProduct_returns200WithList() throws Exception {
        given(stockService.getStockByProduct(100L)).willReturn(List.of(mockStockResponse()));

        mvc.perform(get("/api/v1/stock/products/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].warehouseId").value(1));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void getTotalStock_returns200() throws Exception {
        given(stockService.getTotalStockForProduct(100L)).willReturn(75);

        mvc.perform(get("/api/v1/stock/products/100/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(75));
    }

    // ─── POST adjust ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void adjustStock_validRequest_returns200() throws Exception {
        given(stockService.adjustStock(eq(1L), any())).willReturn(mockStockResponse());

        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.FOUND_STOCK, "Found boxes", null, null);

        mvc.perform(patch("/api/v1/stock/warehouses/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock adjusted successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void bulkUpdateThresholds_validRequest_returns200() throws Exception {
        BulkThresholdUpdateRequest req = new BulkThresholdUpdateRequest(List.of(100L, 200L), 15, 300);
        given(stockService.bulkUpdateThresholds(eq(1L), any())).willReturn(List.of(mockStockResponse()));

        mvc.perform(patch("/api/v1/stock/warehouses/1/thresholds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thresholds updated successfully"))
                .andExpect(jsonPath("$.data[0].productId").value(100));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void adjustStock_insufficientStock_returns409() throws Exception {
        given(stockService.adjustStock(eq(1L), any()))
                .willThrow(new InsufficientStockException(100L, 1L, 100, 50));

        AdjustStockRequest req = new AdjustStockRequest(
                100L, -100, AdjustmentReason.DAMAGED_GOODS, null, null, null);

        mvc.perform(patch("/api/v1/stock/warehouses/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Insufficient stock")));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void adjustStock_missingProductId_returns400() throws Exception {
        mvc.perform(patch("/api/v1/stock/warehouses/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"quantityDelta": 10, "reason": "OTHER"}
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PURCHASE_OFFICER")
    void adjustStock_unauthorizedRole_returns403() throws Exception {
        AdjustStockRequest req = new AdjustStockRequest(
                100L, 10, AdjustmentReason.OTHER, null, null, null);

        mvc.perform(patch("/api/v1/stock/warehouses/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ─── POST transfer ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void transferStock_validRequest_returns200() throws Exception {
        willDoNothing().given(stockService).transferStock(any());

        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, 20, "TXR-001");

        mvc.perform(post("/api/v1/stock/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock transferred successfully"));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void reserve_release_and_receiveStock_return200() throws Exception {
        given(stockService.reserveStock(1L, 100L, 3, "ORDER-1")).willReturn(mockStockResponse());
        given(stockService.releaseReservation(1L, 100L, 2, "ORDER-1")).willReturn(mockStockResponse());
        given(stockService.receiveStock(1L, 100L, 5, "PO-1")).willReturn(mockStockResponse());

        mvc.perform(post("/api/v1/stock/warehouses/1/products/100/reserve")
                        .param("quantity", "3")
                        .param("orderId", "ORDER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock reserved"));

        mvc.perform(post("/api/v1/stock/warehouses/1/products/100/release")
                        .param("quantity", "2")
                        .param("orderId", "ORDER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reservation released"));

        mvc.perform(post("/api/v1/stock/warehouses/1/products/100/receive")
                        .param("quantity", "5")
                        .param("purchaseOrderId", "PO-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stock received"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void initialize_validRequest_returns201() throws Exception {
        given(stockService.initializeStock(1L, 100L, 10, 5, 50)).willReturn(mockStockResponse());

        mvc.perform(post("/api/v1/stock/warehouses/1/products/100/initialize")
                        .param("initialQty", "10")
                        .param("reorderPoint", "5")
                        .param("maxCapacity", "50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Stock level initialized"));
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void transferStock_negativeQuantity_returns400() throws Exception {
        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, -5, null);

        mvc.perform(post("/api/v1/stock/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PURCHASE_OFFICER")
    void transferStock_unauthorizedRole_returns403() throws Exception {
        TransferStockRequest req = new TransferStockRequest(100L, 1L, 2L, 10, null);

        mvc.perform(post("/api/v1/stock/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
