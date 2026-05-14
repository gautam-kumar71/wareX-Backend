package com.inventory.product.exception;

import com.inventory.product.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404Payload() {
        ApiResponse<Void> body = handler.handleNotFound(new NoSuchElementException("missing")).getBody();

        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).isEqualTo("Resource not found");
    }

    @Test
    void handleValidation_concatenatesMessages() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "product");
        bindingResult.addError(new FieldError("product", "name", "Name is required"));
        bindingResult.addError(new FieldError("product", "sku", "SKU is required"));
        Method method = SampleController.class.getDeclaredMethod("save", String.class);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ApiResponse<Void> body = handler.handleValidation(exception).getBody();

        assertThat(body.status()).isEqualTo(400);
        assertThat(body.message()).contains("Name is required", "SKU is required");
    }

    @Test
    void handleIllegalArgument_returns400Payload() {
        ApiResponse<Void> body = handler.handleIllegalArgument(new IllegalArgumentException("bad field")).getBody();

        assertThat(body.status()).isEqualTo(400);
        assertThat(body.message()).isEqualTo("bad field");
    }

    @Test
    void handleGeneral_returns500Payload() {
        ApiResponse<Void> body = handler.handleGeneral(new RuntimeException("boom")).getBody();

        assertThat(body.status()).isEqualTo(500);
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
    }

    private static final class SampleController {
        @SuppressWarnings("unused")
        void save(String body) {
        }
    }
}
