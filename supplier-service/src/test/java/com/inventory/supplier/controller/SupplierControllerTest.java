package com.inventory.supplier.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.inventory.supplier.dto.request.CreateSupplierRequest;
import com.inventory.supplier.dto.request.UpdateSupplierRequest;
import com.inventory.supplier.dto.response.SupplierResponse;
import com.inventory.supplier.exception.DuplicateEmailException;
import com.inventory.supplier.exception.GlobalExceptionHandler;
import com.inventory.supplier.service.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SupplierControllerTest {

    @Mock
    private SupplierService supplierService;

    @InjectMocks
    private SupplierController supplierController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(supplierController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void create_returnsCreatedResponseBody() throws Exception {
        SupplierResponse response = sampleResponse();
        given(supplierService.create(any(CreateSupplierRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSupplierRequest(
                                "Acme Supplies", "Jane Buyer", "ops@acme.com", "9999999999",
                                "Main Street", "Mumbai", "India", "27ABCDE1234F1Z5",
                                30, new BigDecimal("15000"), "Priority", "RAW_MATERIALS"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Supplier created successfully"))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("Acme Supplies"));
    }

    @Test
    void create_returnsBadRequestForInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSupplierRequest(
                                "", "", "bad-email", "", "", "", "", null,
                                -1, new BigDecimal("-1"), null, ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getAll_usesActiveOnlyFlagWhenActiveParameterIsAbsent() throws Exception {
        given(supplierService.getAll(eq(true), any())).willReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/suppliers").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(7));

        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        then(supplierService).should().getAll(captor.capture(), any());
        assertThat(captor.getValue()).isTrue();
    }

    @Test
    void update_returnsConflictWhenDuplicateEmailIsRaised() throws Exception {
        given(supplierService.update(eq(7L), any(UpdateSupplierRequest.class)))
                .willThrow(new DuplicateEmailException("ops@acme.com"));

        mockMvc.perform(put("/api/v1/suppliers/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSupplierRequest(
                                null, null, "ops@acme.com", null, null, null, null,
                                null, null, null, null, null, null
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A supplier with email 'ops@acme.com' already exists"));
    }

    @Test
    void search_returnsPagedResponse() throws Exception {
        given(supplierService.search(eq("acme"), eq(true), any()))
                .willReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/suppliers/search")
                        .param("q", "acme")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contactEmail").value("ops@acme.com"));
    }

    @Test
    void getAllActive_returnsDropdownFriendlyResponse() throws Exception {
        given(supplierService.getAllActive()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/suppliers/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Acme Supplies"));
    }

    @Test
    void deactivate_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/api/v1/suppliers/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Supplier deactivated successfully"));

        then(supplierService).should().deactivate(7L);
    }

    @Test
    void reactivate_returnsSuccessMessage() throws Exception {
        mockMvc.perform(patch("/api/v1/suppliers/7/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Supplier reactivated successfully"));

        then(supplierService).should().reactivate(7L);
    }

    private SupplierResponse sampleResponse() {
        return new SupplierResponse(
                7L,
                "Acme Supplies",
                "Jane Buyer",
                "ops@acme.com",
                "9999999999",
                "Main Street",
                "Mumbai",
                "India",
                "27ABCDE1234F1Z5",
                30,
                new BigDecimal("15000"),
                "Priority vendor",
                "RAW_MATERIALS",
                true,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z")
        );
    }
}
