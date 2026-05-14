package com.inventory.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Gateway-level JWT validation filter.
 *
 * Applied to every route that requires authentication (all except /api/v1/auth/**).
 *
 * What it does:
 *   1. Extracts the Bearer token from the Authorization header
 *   2. Validates the JWT signature and expiry using the shared secret
 *   3. On success: injects X-User-Id, X-User-Role, X-User-Email headers
 *      — downstream services trust these headers and do NOT re-validate the JWT
 *   4. On failure: returns 401 JSON immediately, request never reaches the service
 *
 * This is more efficient than having each microservice validate tokens independently.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (!config.isEnabled()) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // Missing or malformed Authorization header
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                return reject(exchange, HttpStatus.UNAUTHORIZED,
                        "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7).trim();

            try {
                Claims claims = validateToken(token);

                String userId   = claims.getSubject();
                String role     = claims.get("role", String.class);
                String email    = claims.get("email", String.class);
                String fullName = claims.get("fullName", String.class);

                // Inject user context as headers — downstream services read these
                // and never need to touch the JWT again
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id",    userId)
                        .header("X-User-Role",  role)
                        .header("X-User-Email", email != null ? email : "")
                        .header("X-User-Name",  fullName != null ? fullName : "")
                        .build();

                log.debug("JWT valid — userId={}, role={}, path={}",
                        userId, role, request.getPath());

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException ex) {
                log.debug("Expired JWT from {}", request.getRemoteAddress());
                return reject(exchange, HttpStatus.UNAUTHORIZED,
                        "Access token has expired. Please refresh your token.");

            } catch (JwtException ex) {
                log.warn("Invalid JWT from {}: {}", request.getRemoteAddress(), ex.getMessage());
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid access token");
            }
        };
    }

    private Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        String normalizedSecret = jwtSecret == null ? "" : jwtSecret.trim();
        if (normalizedSecret.isEmpty()) {
            throw new IllegalStateException("JWT secret must not be blank");
        }

        try {
            return Keys.hmacShaKeyFor(HexFormat.of().parseHex(normalizedSecret));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(normalizedSecret));
        } catch (IllegalArgumentException ignored) {
        }

        return Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"timestamp":"%s","status":%d,"message":"%s","data":null}
                """.formatted(Instant.now(), status.value(), message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    public static final class Config {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
