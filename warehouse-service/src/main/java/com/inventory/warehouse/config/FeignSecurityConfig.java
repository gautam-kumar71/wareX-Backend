package com.inventory.warehouse.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignSecurityConfig {

    @Bean
    public RequestInterceptor forwardAuthHeaders() {
        return template -> currentRequest().ifPresent(request -> {
            copyHeader(request, template, "Authorization");
            copyHeader(request, template, "X-User-Id");
            copyHeader(request, template, "X-User-Role");
        });
    }

    private java.util.Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return java.util.Optional.of(servletAttributes.getRequest());
        }
        return java.util.Optional.empty();
    }

    private void copyHeader(HttpServletRequest request, feign.RequestTemplate template, String headerName) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            template.header(headerName, value);
        }
    }
}
