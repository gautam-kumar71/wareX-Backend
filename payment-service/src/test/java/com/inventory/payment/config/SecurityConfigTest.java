package com.inventory.payment.config;

import com.inventory.payment.controller.PaymentController;
import com.inventory.payment.dto.PaymentResponse;
import com.inventory.payment.entity.PaymentStatus;
import com.inventory.payment.service.PaymentService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, SecurityConfig.JwtValidationFilter.class})
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void protectedEndpoint_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void userHeadersAuthenticateRequestForReadEndpoint() throws Exception {
        when(paymentService.getAllPayments(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(paymentResponse("TXN-1", "INV-1"))));

        mockMvc.perform(get("/api/v1/payments")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TXN-1"));
    }

    void validBearerTokenAuthenticatesFallbackPath() throws Exception {
        when(paymentService.getAllPayments(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(paymentResponse("TXN-2", "INV-2"))));

        SecretKey key = Keys.hmacShaKeyFor(HexFormat.of().parseHex("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        String token = Jwts.builder()
                .subject("jwt-user")
                .claims(Map.of("role", "ADMIN"))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].invoiceNumber").value("INV-2"));
    }

    @Test
    void invalidBearerTokenFallsBackToUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicPath_isNotUnauthorized() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    private PaymentResponse paymentResponse(String transactionId, String invoiceNumber) {
        return new PaymentResponse(
                1L,
                transactionId,
                invoiceNumber,
                new BigDecimal("100.00"),
                "CARD",
                PaymentStatus.COMPLETED,
                "notes",
                "tester",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
