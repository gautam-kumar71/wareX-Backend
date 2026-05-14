package com.inventory.warehouse.exception;

import com.inventory.warehouse.dto.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_returnsCombinedMessages() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "Name is required"));
        bindingResult.addError(new FieldError("request", "city", "City is required"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(methodParameter(), bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("Name is required", "City is required");
    }

    @Test
    void handleUnreadableBody_returnsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnreadableBody(new HttpMessageNotReadableException("bad body"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Request body is missing or malformed");
    }

    @Test
    void handleMissingParam_returnsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingParam(new MissingServletRequestParameterException("warehouseId", "Long"));

        assertThat(response.getBody().message()).contains("warehouseId");
    }

    @Test
    void handleTypeMismatch_returnsBadRequest() throws Exception {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "warehouseId", methodParameter(), new IllegalArgumentException("bad")
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("warehouseId");
    }

    @Test
    void domainExceptions_mapToExpectedStatusCodes() {
        assertThat(handler.handleWarehouseNotFound(new WarehouseNotFoundException(1L)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleStockNotFound(new StockLevelNotFoundException(1L, 2L)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleInsufficientStock(new InsufficientStockException(3L, 4L, 10, 2)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleDuplicateStock(new DuplicateStockEntryException(5L, 6L)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleEntityNotFound(new EntityNotFoundException("gone")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void illegalArgumentAndState_mapToExpectedStatusCodes() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleIllegalState(new IllegalStateException("conflict")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleOptimisticLock_returnsConflict() {
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException("stockLevel", 99L);

        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("Please retry");
    }

    @Test
    void handleForbidden_returnsForbidden() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleForbidden(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).contains("permission");
    }

    @Test
    void handleGeneral_returnsInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).contains("unexpected error");
    }

    private MethodParameter methodParameter() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sampleMethod(String value) {
    }
}
