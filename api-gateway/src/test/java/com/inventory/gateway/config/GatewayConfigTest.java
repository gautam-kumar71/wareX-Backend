package com.inventory.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayConfigTest {

    private final GatewayConfig gatewayConfig = new GatewayConfig();

    @Test
    void ipKeyResolver_prefersForwardedHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/").header("X-Forwarded-For", "10.0.0.1, 10.0.0.2").build());
        String key = gatewayConfig.ipKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("10.0.0.1");
    }

    @Test
    void ipKeyResolver_fallsBackToRemoteAddress() {
        var request = org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("127.0.0.9", 8080))
                .build();

        String key = gatewayConfig.ipKeyResolver()
                .resolve(org.springframework.mock.web.server.MockServerWebExchange.from(request))
                .block();

        assertThat(key).isEqualTo("127.0.0.9");
    }

    @Test
    void ipKeyResolver_usesUnknownWhenRemoteAddressIsMissing() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        String key = gatewayConfig.ipKeyResolver().resolve(exchange).block();

        assertThat(key).isEqualTo("unknown");
    }

    @Test
    void fallbackRoutes_returnServiceUnavailableBodies() {
        RouterFunction<?> routes = gatewayConfig.fallbackRoutes();
        WebTestClient client = WebTestClient.bindToRouterFunction((RouterFunction) routes).build();

        client.get().uri("/fallback/product")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Product Service is temporarily unavailable."));

        client.post().uri("/fallback/anything")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("temporarily unavailable"));

        client.delete().uri("/fallback/supplier")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Supplier Service is temporarily unavailable."));
    }

    @Test
    void fallbackRoutes_coverAdditionalNamedRoutes() {
        RouterFunction<?> routes = gatewayConfig.fallbackRoutes();
        WebTestClient client = WebTestClient.bindToRouterFunction((RouterFunction) routes).build();

        client.get().uri("/fallback/auth")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Auth Service is temporarily unavailable"));

        client.get().uri("/fallback/payment")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("payment has NOT been processed"));

        client.get().uri("/fallback/report")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Report Service is temporarily unavailable"));

        client.put().uri("/fallback/stock-movement")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Stock Movement Service is temporarily unavailable."));
    }
}
