package com.inventory.supplier.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigTest {

    private final FeignConfig feignConfig = new FeignConfig();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void interceptor_forwardsPresentHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc");
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInterceptor interceptor = feignConfig.authHeaderForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).containsEntry("Authorization", java.util.List.of("Bearer abc"));
        assertThat(template.headers()).containsEntry("X-User-Id", java.util.List.of("42"));
        assertThat(template.headers()).containsEntry("X-User-Role", java.util.List.of("ADMIN"));
    }

    @Test
    void interceptor_skipsWhenNoRequestContextExists() {
        RequestContextHolder.resetRequestAttributes();

        RequestInterceptor interceptor = feignConfig.authHeaderForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }
}
