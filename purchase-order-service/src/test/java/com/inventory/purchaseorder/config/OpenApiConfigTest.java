package com.inventory.purchaseorder.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void annotationsExposeExpectedMetadata() {
        OpenAPIDefinition definition = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);
        SecurityScheme scheme = OpenApiConfig.class.getAnnotation(SecurityScheme.class);

        assertThat(definition.info().title()).isEqualTo("Purchase Order Service API");
        assertThat(definition.servers()[0].url()).isEqualTo("http://localhost:8085");
        assertThat(scheme.name()).isEqualTo("bearerAuth");
        assertThat(new OpenApiConfig()).isNotNull();
    }
}
