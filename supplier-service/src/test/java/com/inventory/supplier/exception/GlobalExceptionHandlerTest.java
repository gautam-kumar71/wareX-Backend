package com.inventory.supplier.exception;

import com.inventory.supplier.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_returnsJoinedMessages() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "Name is required"));
        bindingResult.addError(new FieldError("request", "email", "Email is invalid"));
        Method method = SampleController.class.getDeclaredMethod("sample", Object.class);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("Name is required", "Email is invalid");
    }

    @Test
    void handleUnreadable_returnsFriendlyMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnreadable(new HttpMessageNotReadableException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Request body is missing or malformed");
    }

    @Test
    void handleForbidden_returnsForbiddenMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleForbidden(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).contains("permission");
    }

    @Test
    void handleGeneral_returnsGenericError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    static class SampleController {
        @SuppressWarnings("unused")
        void sample(Object request) {
        }
    }
}
