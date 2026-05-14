package com.inventory.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Configuration
public class GatewayConfig {

    /**
     * Rate limiter key resolver.
     *
     * Uses the caller's IP address as the rate limit key.
     * In production behind a load balancer, use X-Forwarded-For instead:
     *   exchange.getRequest().getHeaders().getFirst("X-Forwarded-For")
     *
     * You can also key by userId (from X-User-Id header) for per-user limits
     * on authenticated routes.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // Try X-Forwarded-For first (behind reverse proxy)
            String forwarded = exchange.getRequest()
                    .getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            // Fallback to direct remote address
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(remoteAddr);
        };
    }

    /**
     * Fallback routes — returned when a circuit breaker trips.
     * Each downstream service has its own fallback endpoint.
     */
    @Bean
    public RouterFunction<ServerResponse> fallbackRoutes() {
        return RouterFunctions.route()

                .route(RequestPredicates.path("/fallback/auth"), req ->
                        fallbackResponse("Auth Service is temporarily unavailable. Please try again shortly."))

                .route(RequestPredicates.path("/fallback/warehouse"), req ->
                        fallbackResponse("Warehouse Service is temporarily unavailable."))

                .route(RequestPredicates.path("/fallback/product"), req ->
                        fallbackResponse("Product Service is temporarily unavailable."))

                .route(RequestPredicates.path("/fallback/purchase-order"), req ->
                        fallbackResponse("Purchase Order Service is temporarily unavailable."))

                .route(RequestPredicates.path("/fallback/payment"), req ->
                        fallbackResponse("Payment Service is temporarily unavailable. Your payment has NOT been processed."))

                .route(RequestPredicates.path("/fallback/supplier"), req ->
                        fallbackResponse("Supplier Service is temporarily unavailable."))

                .route(RequestPredicates.path("/fallback/alert"), req ->
                        fallbackResponse("Alert Service is temporarily unavailable."))

                .route(RequestPredicates.path("/fallback/report"), req ->
                        fallbackResponse("Report Service is temporarily unavailable. Please try again later."))

                .route(RequestPredicates.path("/fallback/stock-movement"), req ->
                        fallbackResponse("Stock Movement Service is temporarily unavailable."))

                .route(RequestPredicates.path("/fallback/{service}"), req ->
                        fallbackResponse("The requested service is temporarily unavailable. Please retry."))

                .build();
    }

    private Mono<ServerResponse> fallbackResponse(String message) {
        String body = """
                {"timestamp":"%s","status":503,"message":"%s","data":null}
                """.formatted(Instant.now(), message);

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }
}
