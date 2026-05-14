package com.inventory.stockmovement.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtValidationFilter jwtFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/api-docs/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write(
                                    "{\"timestamp\":\"%s\",\"status\":401,\"message\":\"Unauthorized\",\"data\":null}"
                                            .formatted(Instant.now()));
                        }))
                .build();
    }

    @Component
    @Slf4j
    static class JwtValidationFilter extends OncePerRequestFilter {

        @Value("${jwt.secret}")
        private String secret;

        private SecretKey signingKey() {
            String normalizedSecret = secret == null ? "" : secret.trim();
            if (normalizedSecret.isEmpty()) {
                throw new IllegalStateException("JWT secret must not be blank");
            }

            try {
                return Keys.hmacShaKeyFor(HexFormat.of().parseHex(normalizedSecret));
            } catch (IllegalArgumentException ignored) {
            }

            try {
                return Keys.hmacShaKeyFor(Decoders.BASE64.decode(normalizedSecret));
            } catch (IllegalArgumentException ignored) {
            }

            return Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {
            String userId = request.getHeader("X-User-Id");
            String role   = request.getHeader("X-User-Role");
            if (userId != null && role != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                chain.doFilter(request, response);
                return;
            }
            String header = request.getHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                try {
                    Claims claims = Jwts.parser().verifyWith(signingKey()).build()
                            .parseSignedClaims(header.substring(7).trim()).getPayload();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null,
                                    List.of(new SimpleGrantedAuthority(
                                            "ROLE_" + claims.get("role", String.class)))));
                } catch (JwtException ex) {
                    log.debug("JWT validation failed: {}", ex.getMessage());
                }
            }
            chain.doFilter(request, response);
        }
    }
}
