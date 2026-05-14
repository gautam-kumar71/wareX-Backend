package com.inventory.warehouse.config;

import com.inventory.warehouse.controller.WarehouseController;
import com.inventory.warehouse.exception.GlobalExceptionHandler;
import com.inventory.warehouse.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WarehouseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WarehouseService warehouseService;

    @Test
    void unauthenticatedRequest_returnsJson401() throws Exception {
        mvc.perform(get("/api/v1/warehouses"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":401")));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_STAFF")
    void forbiddenRequest_returnsJson403() throws Exception {
        mvc.perform(delete("/api/v1/warehouses/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":403")));
    }

    @Test
    void publicSwaggerPath_isPermittedBySecurityChain() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is5xxServerError());
    }
}
