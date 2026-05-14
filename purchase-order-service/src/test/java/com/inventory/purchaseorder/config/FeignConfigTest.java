package com.inventory.purchaseorder.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void interceptor_forwardsAuthAndUserHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-User-Id", "7");
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInterceptor interceptor = new FeignConfig().authHeaderForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(first(template, "Authorization")).isEqualTo("Bearer token");
        assertThat(first(template, "X-User-Id")).isEqualTo("7");
        assertThat(first(template, "X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void interceptor_withoutRequestContextDoesNothing() {
        RequestInterceptor interceptor = new FeignConfig().authHeaderForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    private String first(RequestTemplate template, String name) {
        Collection<String> values = template.headers().get(name);
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }
}
