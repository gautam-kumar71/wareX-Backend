package com.inventory.purchaseorder.exception;

import com.inventory.purchaseorder.dto.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationAndRequestErrors_mapToBadRequest() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "supplierId", "Supplier ID is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter(), bindingResult);

        assertThat(handler.handleValidation(ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleUnreadable(new HttpMessageNotReadableException("bad")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleMissingHeader(new MissingRequestHeaderException("X-User-Id", methodParameter())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("abc", Long.class, "id", methodParameter(), new IllegalArgumentException())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void domainAndGeneralExceptions_mapToExpectedStatuses() {
        assertThat(handler.handlePONotFound(new PurchaseOrderNotFoundException(1L)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleEntityNotFound(new EntityNotFoundException("missing")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleInvalidTransition(new InvalidStatusTransitionException(
                com.inventory.purchaseorder.enums.PurchaseOrderStatus.DRAFT,
                com.inventory.purchaseorder.enums.PurchaseOrderStatus.RECEIVED
        )).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleSupplierValidation(new SupplierValidationException("bad supplier")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleWarehouseUnavailable(new WarehouseServiceUnavailableException("warehouse down")).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("bad")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleIllegalState(new IllegalStateException("bad")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleForbidden(new AccessDeniedException("forbidden")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException("boom"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private MethodParameter methodParameter() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
