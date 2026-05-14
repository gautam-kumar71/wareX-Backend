package com.inventory.auth.exception;

import com.inventory.auth.dto.response.ApiResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
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
        bindingResult.addError(new FieldError("request", "email", "Email required"));
        MethodArgumentNotValidException validation = new MethodArgumentNotValidException(methodParameter(), bindingResult);

        assertThat(handler.handleValidation(validation).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleUnreadableBody(new HttpMessageNotReadableException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleMissingHeader(new MissingRequestHeaderException("Authorization", methodParameter())).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException("x", Long.class, "id", methodParameter(), new IllegalArgumentException())).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void authAndDomainExceptions_mapToExpectedStatuses() {
        assertThat(handler.handleBadCredentials(new BadCredentialsException("nope")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleExpiredToken(new ExpiredJwtException(null, null, "expired")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleInvalidToken(new JwtException("bad")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleTokenError(new TokenException("bad refresh")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleOAuth2Error(new OAuth2AuthenticationException("oauth")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleForbidden(new AccessDeniedException("forbidden")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleUserExists(new UserAlreadyExistsException("exists")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleInvalidOtp(new InvalidOtpException("otp")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleNotificationDelivery(new NotificationDeliveryException("mail", new RuntimeException("smtp"))).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.handleNotFound(new EntityNotFoundException("missing")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void generalException_mapsToInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).contains("unexpected error");
    }

    private MethodParameter methodParameter() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
    }
}
