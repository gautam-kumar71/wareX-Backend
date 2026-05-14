package com.inventory.warehouse.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class FeignSecurityConfigTest {

    private final FeignSecurityConfig config = new FeignSecurityConfig();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardAuthHeaders_copiesGatewayAndAuthHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInterceptor interceptor = config.forwardAuthHeaders();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(firstHeader(template, "Authorization")).isEqualTo("Bearer token");
        assertThat(firstHeader(template, "X-User-Id")).isEqualTo("42");
        assertThat(firstHeader(template, "X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void forwardAuthHeaders_ignoresBlankHeadersAndMissingRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", " ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInterceptor interceptor = config.forwardAuthHeaders();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");

        RequestContextHolder.resetRequestAttributes();
        interceptor.apply(template);
        assertThat(template.headers()).isEmpty();
    }

    private String firstHeader(RequestTemplate template, String name) {
        Collection<String> values = template.headers().get(name);
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }
}
