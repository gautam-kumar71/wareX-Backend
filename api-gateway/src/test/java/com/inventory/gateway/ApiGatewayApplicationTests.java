package com.inventory.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGatewayApplicationTests {

    @Test
    void applicationClass_isAvailable() {
        assertThat(ApiGatewayApplication.class).isNotNull();
    }
}
