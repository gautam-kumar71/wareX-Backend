package com.inventory.auth.security;

import com.inventory.auth.service.TokenBlocklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private TokenBlocklistService blocklist;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_setsAuthentication() throws Exception {
        JwtProvider provider = provider();
        JwtAuthFilter filter = new JwtAuthFilter(provider, blocklist);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token("42", "ADMIN"));

        given(blocklist.isBlocked(org.mockito.ArgumentMatchers.anyString())).willReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("42");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN");
    }

    @Test
    void blockedToken_returns401AndShortCircuits() throws Exception {
        JwtProvider provider = provider();
        JwtAuthFilter filter = new JwtAuthFilter(provider, blocklist);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        request.addHeader("Authorization", "Bearer " + token("42", "ADMIN"));

        given(blocklist.isBlocked(org.mockito.ArgumentMatchers.anyString())).willReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("revoked");
    }

    @Test
    void invalidToken_clearsContextAndContinues() throws Exception {
        JwtProvider provider = provider();
        JwtAuthFilter filter = new JwtAuthFilter(provider, blocklist);
        MockHttpServletRequest request = new MockHttpServletRequest();
        FilterChain chain = mock(FilterChain.class);
        request.addHeader("Authorization", "Bearer invalid-token");
        given(blocklist.isBlocked(org.mockito.ArgumentMatchers.anyString())).willReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private JwtProvider provider() {
        JwtProvider provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "secret", SECRET);
        ReflectionTestUtils.setField(provider, "accessExpiryMs", 900_000L);
        ReflectionTestUtils.setField(provider, "refreshExpiryMs", 604_800_000L);
        return provider;
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
