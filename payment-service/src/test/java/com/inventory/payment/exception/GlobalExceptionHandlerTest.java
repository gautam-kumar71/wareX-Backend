package com.inventory.payment.exception;

import com.inventory.payment.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException_returns500WithMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleRuntimeException(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("boom");
    }

    @Test
    void handleValidation_joinsFieldMessages() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "payment");
        bindingResult.addError(new FieldError("payment", "invoiceNumber", "Invoice number is required"));
        bindingResult.addError(new FieldError("payment", "amount", "Amount must be greater than zero"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Invoice number is required");
        assertThat(response.getBody().message()).contains("Amount must be greater than zero");
    }

    @Test
    void handleGeneral_returnsGeneric500Message() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(
                new MethodArgumentTypeMismatchException("x", String.class, "id", null, new IllegalArgumentException("bad"))
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
