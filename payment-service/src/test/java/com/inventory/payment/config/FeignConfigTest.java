package com.inventory.payment.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void interceptor_forwardsAuthAndUserHeadersWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("X-User-Id", "u-1");
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestInterceptor interceptor = new FeignConfig().authHeaderForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer token");
        assertThat(template.headers().get("X-User-Id")).containsExactly("u-1");
        assertThat(template.headers().get("X-User-Role")).containsExactly("ADMIN");
    }

    @Test
    void interceptor_doesNothingWhenNoRequestContextExists() {
        RequestContextHolder.resetRequestAttributes();

        RequestInterceptor interceptor = new FeignConfig().authHeaderForwardingInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }
}
