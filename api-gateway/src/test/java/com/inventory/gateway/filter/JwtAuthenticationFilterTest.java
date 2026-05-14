package com.inventory.gateway.filter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationFilterTest {

    private static final String SECURE_PATH = "/secure";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
    private final String secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void apply_rejectsMissingAuthorizationHeader() {
        setJwtSecret(secret);
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, passthroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apply_injectsHeadersForValidToken() {
        setJwtSecret(secret);
        AtomicReference<String> userIdHeader = new AtomicReference<>();
        AtomicReference<String> roleHeader = new AtomicReference<>();
        AtomicReference<String> emailHeader = new AtomicReference<>();
        MockServerWebExchange exchange = exchangeWithBearer(token(expiryInFuture()));
        GatewayFilter gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());

        StepVerifier.create(gatewayFilter.filter(exchange, ex -> {
            userIdHeader.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            roleHeader.set(ex.getRequest().getHeaders().getFirst("X-User-Role"));
            emailHeader.set(ex.getRequest().getHeaders().getFirst("X-User-Email"));
            return Mono.empty();
        })).verifyComplete();

        assertThat(userIdHeader.get()).isEqualTo("gateway-user");
        assertThat(roleHeader.get()).isEqualTo("ADMIN");
        assertThat(emailHeader.get()).isEqualTo("user@example.com");
    }

    @Test
    void apply_rejectsExpiredToken() {
        setJwtSecret(secret);
        MockServerWebExchange exchange = exchangeWithBearer(token(Date.from(Instant.now().minusSeconds(60))));

        StepVerifier.create(filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, passthroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("expired");
    }

    @Test
    void apply_rejectsInvalidToken() {
        setJwtSecret(secret);
        MockServerWebExchange exchange = exchangeWithBearer("nope");

        StepVerifier.create(filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, passthroughChain()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Invalid access token");
    }

    @Test
    void apply_rejectsBlankSecretWhenTokenValidationIsNeeded() {
        setJwtSecret(" ");
        MockServerWebExchange exchange = exchangeWithBearer("token");

        assertThatThrownBy(() -> filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, passthroughChain()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void apply_usesUtf8SecretFallbackAndBlankOptionalClaims() {
        String base64Secret = Base64.getEncoder().encodeToString("base64-secret-key-for-gateway-signing-1234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        setJwtSecret(base64Secret);
        AtomicReference<String> userIdHeader = new AtomicReference<>();
        AtomicReference<String> emailHeader = new AtomicReference<>();
        AtomicReference<String> nameHeader = new AtomicReference<>();
        MockServerWebExchange exchange = exchangeWithBearer(base64Token(base64Secret, expiryInFuture()));

        StepVerifier.create(filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, ex -> {
            userIdHeader.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            emailHeader.set(ex.getRequest().getHeaders().getFirst("X-User-Email"));
            nameHeader.set(ex.getRequest().getHeaders().getFirst("X-User-Name"));
            return Mono.empty();
        })).verifyComplete();

        assertThat(userIdHeader.get()).isEqualTo("gateway-user");
        assertThat(emailHeader.get()).isEqualTo("");
        assertThat(nameHeader.get()).isEqualTo("");
    }

    @Test
    void apply_skipsValidationWhenFilterIsDisabled() {
        JwtAuthenticationFilter.Config config = new JwtAuthenticationFilter.Config();
        config.setEnabled(false);
        MockServerWebExchange exchange = exchange();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        StepVerifier.create(filter.apply(config).filter(exchange, ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(chainInvoked.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private GatewayFilterChain passthroughChain() {
        return exchange -> Mono.empty();
    }

    private void setJwtSecret(String value) {
        ReflectionTestUtils.setField(filter, "jwtSecret", value);
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get(SECURE_PATH).build());
    }

    private MockServerWebExchange exchangeWithBearer(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(SECURE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
    }

    private Date expiryInFuture() {
        return Date.from(Instant.now().plusSeconds(120));
    }

    private String token(Date expiration) {
        var key = Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret));
        return Jwts.builder()
                .subject("gateway-user")
                .claim("role", "ADMIN")
                .claim("email", "user@example.com")
                .claim("fullName", "Gateway User")
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String base64Token(String base64Secret, Date expiration) {
        byte[] signingBytes = Base64.getDecoder().decode(base64Secret);
        return Jwts.builder()
                .subject("gateway-user")
                .claim("role", "ADMIN")
                .expiration(expiration)
                .signWith(Keys.hmacShaKeyFor(signingBytes), Jwts.SIG.HS256)
                .compact();
    }

}
