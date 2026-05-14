package com.inventory.stockmovement.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void configurationClass_canBeInstantiated() {
        assertThat(new OpenApiConfig()).isNotNull();
    }
}
