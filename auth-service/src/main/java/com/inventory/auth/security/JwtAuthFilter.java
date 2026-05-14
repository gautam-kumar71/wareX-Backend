package com.inventory.auth.security;

import com.inventory.auth.service.TokenBlocklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final TokenBlocklistService blocklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Debug logging
        if (request.getRequestURI().endsWith("/me")) {
            log.info("[JwtAuthFilter] Auth Header present: {}", StringUtils.hasText(request.getHeader("Authorization")));
        }

        String token = extractBearerToken(request);

        if (token != null) {
            try {
                // Check Redis blocklist first (logged-out tokens)
                if (blocklist.isBlocked(token)) {
                    log.warn("Revoked token used — IP: {}, path: {}",
                            request.getRemoteAddr(), request.getRequestURI());
                    writeUnauthorized(response, "Token has been revoked");
                    return;
                }

                Claims claims = jwtProvider.validateAndExtract(token);

                String userId = claims.getSubject();
                String role   = claims.get("role", String.class);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException ex) {
                // Expired, malformed, or invalid signature — clear context and let
                // Spring Security's authenticationEntryPoint return 401
                log.debug("JWT validation failed: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","status":401,"message":"%s","data":null}
                """.formatted(Instant.now(), message));
    }
}