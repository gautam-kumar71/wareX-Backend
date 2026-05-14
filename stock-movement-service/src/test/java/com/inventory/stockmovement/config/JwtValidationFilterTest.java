package com.inventory.stockmovement.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationFilterTest {

    private final SecurityConfig.JwtValidationFilter filter = new SecurityConfig.JwtValidationFilter();
    private final String hexSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_usesGatewayHeadersWhenPresent() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "secret", hexSecret);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Role", "ADMIN");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void doFilterInternal_leavesContextEmptyForInvalidJwt() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "secret", hexSecret);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_rejectsBlankSecretWhenJwtMustBeValidated() {
        ReflectionTestUtils.setField(filter, "secret", "  ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer anything");

        assertThatThrownBy(() -> filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }
}
