package com.inventory.warehouse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.warehouse.config.SecurityConfig;
import com.inventory.warehouse.dto.request.CreateWarehouseRequest;
import com.inventory.warehouse.dto.request.UpdateWarehouseRequest;
import com.inventory.warehouse.dto.response.WarehouseResponse;
import com.inventory.warehouse.exception.GlobalExceptionHandler;
import com.inventory.warehouse.exception.WarehouseNotFoundException;
import com.inventory.warehouse.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WarehouseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
class WarehouseControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WarehouseService warehouseService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returnsCreatedResponse() throws Exception {
        given(warehouseService.createWarehouse(any(CreateWarehouseRequest.class))).willReturn(response(1L));

        CreateWarehouseRequest request = new CreateWarehouseRequest(
                "Main", "Plot 1", "Pune", "India", 100, "Alex", "9999"
        );

        mvc.perform(post("/api/v1/warehouses")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Warehouse created successfully"))
                .andExpect(jsonPath("$.data.name").value("Warehouse 1"));
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void getAll_returnsList() throws Exception {
        given(warehouseService.getAllWarehouses(true)).willReturn(List.of(response(1L)));

        mvc.perform(get("/api/v1/warehouses").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].capacityPercent").value(45));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void getById_returnsWarehouse() throws Exception {
        given(warehouseService.getWarehouseById(3L)).willReturn(response(3L));

        mvc.perform(get("/api/v1/warehouses/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void update_returnsUpdatedWarehouse() throws Exception {
        given(warehouseService.updateWarehouse(eq(4L), any(UpdateWarehouseRequest.class))).willReturn(response(4L));

        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Updated", "New Plot", "Mumbai", "India", 500, "Sam", "8888", true
        );

        mvc.perform(put("/api/v1/warehouses/4")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Warehouse updated successfully"))
                .andExpect(jsonPath("$.data.id").value(4));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivate_returnsSuccessMessage() throws Exception {
        willDoNothing().given(warehouseService).deactivateWarehouse(7L);

        mvc.perform(delete("/api/v1/warehouses/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Warehouse deactivated successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reactivate_returnsWarehouse() throws Exception {
        given(warehouseService.reactivateWarehouse(8L)).willReturn(response(8L));

        mvc.perform(post("/api/v1/warehouses/8/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Warehouse reactivated successfully"))
                .andExpect(jsonPath("$.data.id").value(8));
    }

    @Test
    void protectedEndpoint_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/v1/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void adminOnlyEndpoint_withWrongRoleReturns403() throws Exception {
        mvc.perform(delete("/api/v1/warehouses/7"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_whenMissingMapsTo404() throws Exception {
        given(warehouseService.getWarehouseById(55L)).willThrow(new WarehouseNotFoundException(55L));

        mvc.perform(get("/api/v1/warehouses/55"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("55")));
    }

    @Test
    void publicDocsPath_isNotBlockedBySecurity() throws Exception {
        mvc.perform(get("/api-docs/ping"))
                .andExpect(status().is5xxServerError());
    }

    private WarehouseResponse response(Long id) {
        return new WarehouseResponse(
                id,
                "Warehouse " + id,
                "Plot",
                "Pune",
                "India",
                100,
                45,
                45,
                1,
                0,
                "Manager",
                "9999",
                null,
                null,
                null,
                null,
                true,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }
}
