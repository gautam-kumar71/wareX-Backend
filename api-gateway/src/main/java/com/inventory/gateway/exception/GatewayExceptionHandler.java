package com.inventory.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Catches unhandled exceptions from the gateway layer itself
 * (not from downstream services — those return their own error responses).
 *
 * Common cases:
 *  - Service not found in Eureka (NotFoundException) → 503
 *  - Connection refused / timeout → 504
 *  - Route not found → 404
 *
 * Order(-1) means this runs before Spring Boot's default error handler.
 */
@Component
@Order(-1)
@Slf4j
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        HttpStatus status;
        String message;

        if (ex instanceof NotFoundException) {
            status  = HttpStatus.SERVICE_UNAVAILABLE;
            message = "The requested service is not available. It may be starting up — please retry.";
            log.warn("Service not found in registry: {}", ex.getMessage());

        } else if (ex instanceof ResponseStatusException rse) {
            status  = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : ex.getMessage();

        } else if (ex instanceof java.net.ConnectException) {
            status  = HttpStatus.BAD_GATEWAY;
            message = "Could not connect to the upstream service. Please try again.";
            log.error("Connection refused to upstream: {}", ex.getMessage());

        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            status  = HttpStatus.GATEWAY_TIMEOUT;
            message = "The upstream service did not respond in time. Please try again.";
            log.warn("Gateway timeout: {}", ex.getMessage());

        } else {
            status  = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected gateway error occurred.";
            log.error("Unhandled gateway exception", ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"timestamp":"%s","status":%d,"message":"%s","data":null}
                """.formatted(Instant.now(), status.value(), message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}