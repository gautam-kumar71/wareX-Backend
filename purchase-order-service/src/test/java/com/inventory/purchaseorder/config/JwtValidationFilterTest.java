package com.inventory.purchaseorder.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JwtValidationFilterTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gatewayHeaders_createAuthentication() throws Exception {
        SecurityConfig.JwtValidationFilter filter = filter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "9");
        request.addHeader("X-User-Role", "ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("9");
    }

    @Test
    void validJwt_setsAuthentication() throws Exception {
        SecurityConfig.JwtValidationFilter filter = filter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token("5", "PURCHASE_OFFICER"));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("5");
    }

    @Test
    void invalidJwt_leavesContextEmpty() throws Exception {
        SecurityConfig.JwtValidationFilter filter = filter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankSecret_throwsWhenJwtNeedsValidation() {
        SecurityConfig.JwtValidationFilter filter = filter(" ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer anything");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    private SecurityConfig.JwtValidationFilter filter(String secret) {
        SecurityConfig.JwtValidationFilter filter = new SecurityConfig.JwtValidationFilter();
        ReflectionTestUtils.setField(filter, "secret", secret);
        return filter;
    }

    private String token(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(HexFormat.of().parseHex(SECRET)))
                .compact();
    }
}
