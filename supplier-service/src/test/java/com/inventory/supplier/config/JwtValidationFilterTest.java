package com.inventory.supplier.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

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
    void doFilterInternal_authenticatesValidJwtSignedWithHexSecret() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "secret", hexSecret);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + Jwts.builder()
                .subject("jwt-user")
                .claim("role", "MANAGER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(HexFormat.of().parseHex(hexSecret)))
                .compact());

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo("jwt-user");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_MANAGER");
    }

    @Test
    void doFilterInternal_authenticatesValidJwtSignedWithBase64Secret() throws ServletException, IOException {
        byte[] secretBytes = "base64-secret-base64-secret-1234".getBytes();
        ReflectionTestUtils.setField(filter, "secret", Base64.getEncoder().encodeToString(secretBytes));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + Jwts.builder()
                .subject("jwt-user")
                .claim("role", "BUYER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secretBytes))
                .compact());

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("jwt-user");
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
