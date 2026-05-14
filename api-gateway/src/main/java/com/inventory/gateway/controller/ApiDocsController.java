package com.inventory.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/internal/docs")
public class ApiDocsController {

    private static final String PATH_DELIMITER = "/";
    private static final String OPEN_API_PATH = "/api-docs";

    @Value("${docs.base-url}")
    private String gatewayBaseUrl;

    @Value("${springdoc.swagger-ui.path:/swagger}")
    private String swaggerPath;

    @GetMapping("/services")
    public ResponseEntity<DocsCatalogResponse> getServiceDocs(ServerHttpRequest request) {
        String gatewayUrl = resolveGatewayUrl(request);
        String normalizedSwaggerPath = swaggerPath.startsWith(PATH_DELIMITER)
                ? swaggerPath
                : PATH_DELIMITER + swaggerPath;

        return ResponseEntity.ok(new DocsCatalogResponse(
                gatewayUrl,
                gatewayUrl + normalizedSwaggerPath,
                gatewayUrl + OPEN_API_PATH,
                List.of(
                        doc("auth-service", "Auth Service", "Authentication, registration, password reset, profile APIs", gatewayUrl, false),
                        doc("warehouse-service", "Warehouse Service", "Warehouse and stock management APIs", gatewayUrl, false),
                        doc("product-service", "Product Service", "Catalog and product master APIs", gatewayUrl, false),
                        doc("purchase-order-service", "Purchase Order Service", "Purchase order lifecycle and invoices APIs", gatewayUrl, false),
                        doc("supplier-service", "Supplier Service", "Supplier master and status APIs", gatewayUrl, false),
                        doc("stock-movement-service", "Stock Movement Service", "Inventory movement history and audit APIs", gatewayUrl, false),
                        doc("payment-service", "Payment Service", "Manual and Razorpay payment APIs", gatewayUrl, false),
                        doc("alert-service", "Alert Service", "Alert inbox and read-state APIs", gatewayUrl, false),
                        doc("report-service", "Report Service", "Dashboard and export/reporting APIs", gatewayUrl, false)
                )
        ));
    }

    private ServiceDocLink doc(String serviceId, String displayName, String description, String gatewayUrl, boolean gateway) {
        String normalizedBaseUrl = trimTrailingSlash(gatewayUrl);
        String gatewayOpenApiUrl = gatewayUrl + "/service-docs/" + serviceId + OPEN_API_PATH;
        return new ServiceDocLink(
                serviceId,
                displayName,
                description,
                gateway,
                normalizedBaseUrl,
                "",
                "",
                gatewayOpenApiUrl,
                gatewayOpenApiUrl,
                ""
        );
    }

    private String resolveGatewayUrl(ServerHttpRequest request) {
        if (request != null) {
            String forwardedProto = firstHeaderValue(request, "X-Forwarded-Proto");
            String forwardedHost = firstHeaderValue(request, "X-Forwarded-Host");
            String forwardedPort = firstHeaderValue(request, "X-Forwarded-Port");

            if (hasText(forwardedProto) && hasText(forwardedHost)) {
                return trimTrailingSlash(buildUrl(forwardedProto, forwardedHost, forwardedPort));
            }

            URI requestUri = request.getURI();
            if (hasText(requestUri.getScheme()) && hasText(requestUri.getHost())) {
                return trimTrailingSlash(buildUrl(requestUri.getScheme(), requestUri.getHost(), requestUri.getPort()));
            }
        }

        return trimTrailingSlash(gatewayBaseUrl);
    }

    private String firstHeaderValue(ServerHttpRequest request, String headerName) {
        String headerValue = request.getHeaders().getFirst(headerName);
        if (!hasText(headerValue)) {
            return "";
        }

        return headerValue.split(",")[0].trim();
    }

    private String buildUrl(String scheme, String host, String port) {
        String normalizedHost = host.trim();
        if (normalizedHost.contains("://")) {
            normalizedHost = normalizedHost.substring(normalizedHost.indexOf("://") + 3);
        }

        Integer parsedPort = parsePort(port);
        if (parsedPort != null && !normalizedHost.contains(":") && !isDefaultPort(scheme, parsedPort)) {
            normalizedHost = normalizedHost + ":" + parsedPort;
        }

        return scheme.trim() + "://" + normalizedHost;
    }

    private String buildUrl(String scheme, String host, int port) {
        if (port <= 0 || isDefaultPort(scheme, port)) {
            return scheme + "://" + host;
        }

        return scheme + "://" + host + ":" + port;
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Integer parsePort(String value) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record DocsCatalogResponse(
            String gatewayBaseUrl,
            String gatewaySwaggerUrl,
            String gatewayOpenApiUrl,
            List<ServiceDocLink> services
    ) {}

    public record ServiceDocLink(
            String serviceId,
            String displayName,
            String description,
            boolean gateway,
            String baseUrl,
            String swaggerUiUrl,
            String swaggerIndexUrl,
            String openApiUrl,
            String gatewayOpenApiUrl,
            String healthUrl
    ) {}
}
