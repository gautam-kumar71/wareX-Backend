package com.inventory.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Gateway Security Config.
 *
 * The gateway uses Spring WebFlux (reactive), so we use ServerHttpSecurity
 * NOT the servlet-based HttpSecurity.
 *
 * JWT validation is handled by our custom JwtAuthenticationFilter (a
 * GatewayFilter), not here. Spring Security here is intentionally
 * permissive — it just disables CSRF (stateless API) and lets all
 * requests through. The JwtAuthenticationFilter on each route does
 * the actual token validation before forwarding to the downstream service.
 *
 * Why not handle JWT here?
 * GatewayFilters can modify the request (inject headers) BEFORE forwarding.
 * Spring Security filters run at a different point and cannot easily
 * mutate the outbound request to add X-User-Id etc.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Stateless API — CSRF not needed
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // JWT validation is done in JwtAuthenticationFilter per route
                // Gateway itself permits all — filter handles auth
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                // No form login, no HTTP Basic at gateway level
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
                HttpMethod.HEAD.name()
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
