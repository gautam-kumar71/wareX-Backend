package com.inventory.gateway.exception;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void handle_mapsNotFoundToServiceUnavailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/missing").build());

        StepVerifier.create(handler.handle(exchange, NotFoundException.create(false, "service missing")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("not available");
    }

    @Test
    void handle_preservesResponseStatusExceptionStatusAndReason() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/bad").build());

        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Bad request");
    }

    @Test
    void handle_mapsConnectExceptionToBadGateway() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/upstream").build());

        StepVerifier.create(handler.handle(exchange, new ConnectException("refused")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Could not connect");
    }

    @Test
    void handle_mapsTimeoutToGatewayTimeout() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/timeout").build());

        StepVerifier.create(handler.handle(exchange, new TimeoutException("slow")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("did not respond in time");
    }

    @Test
    void handle_mapsUnknownErrorsToInternalServerError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/boom").build());

        StepVerifier.create(handler.handle(exchange, new IllegalStateException("boom")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("unexpected gateway error");
    }
}
