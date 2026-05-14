package com.inventory.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsControllerTest {

    @Test
    void getServiceDocs_exposesGatewaySwaggerAndProxyUrls() {
        ApiDocsController controller = new ApiDocsController();
        ReflectionTestUtils.setField(controller, "gatewayBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(controller, "swaggerPath", "/swagger");

        ApiDocsController.DocsCatalogResponse body = controller.getServiceDocs().getBody();

        assertThat(body).isNotNull();
        assertThat(body.gatewaySwaggerUrl()).isEqualTo("http://localhost:8080/swagger");
        assertThat(body.gatewayOpenApiUrl()).isEqualTo("http://localhost:8080/api-docs");
        assertThat(body.services()).hasSize(9);
        assertThat(body.services().get(0).gatewayOpenApiUrl())
                .isEqualTo("http://localhost:8080/service-docs/auth-service/api-docs");
        assertThat(body.services().get(0).openApiUrl())
                .isEqualTo("http://localhost:8080/service-docs/auth-service/api-docs");
        assertThat(body.services().get(0).swaggerUiUrl()).isEmpty();
        assertThat(body.services().get(0).healthUrl()).isEmpty();
    }

    @Test
    void getServiceDocs_normalizesTrailingSlashAndMissingLeadingSlash() {
        ApiDocsController controller = new ApiDocsController();
        ReflectionTestUtils.setField(controller, "gatewayBaseUrl", "http://localhost:8080/");
        ReflectionTestUtils.setField(controller, "swaggerPath", "swagger");

        ApiDocsController.DocsCatalogResponse body = controller.getServiceDocs().getBody();

        assertThat(body).isNotNull();
        assertThat(body.gatewayBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(body.gatewaySwaggerUrl()).isEqualTo("http://localhost:8080/swagger");
    }
}
