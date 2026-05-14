package com.inventory.warehouse.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void annotationsExposeExpectedMetadata() {
        OpenAPIDefinition definition = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);
        SecurityScheme securityScheme = OpenApiConfig.class.getAnnotation(SecurityScheme.class);

        assertThat(definition).isNotNull();
        assertThat(definition.info().title()).isEqualTo("Warehouse Service API");
        assertThat(definition.servers()[0].url()).isEqualTo("http://localhost:8082");
        assertThat(securityScheme).isNotNull();
        assertThat(securityScheme.name()).isEqualTo("bearerAuth");
        assertThat(new OpenApiConfig()).isNotNull();
    }
}
