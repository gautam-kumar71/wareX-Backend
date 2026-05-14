package com.inventory.eureka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Eureka clients POST to /eureka/** to register and send heartbeats.
     * We need:
     *   - Basic auth on all endpoints (dashboard + registry API)
     *   - CSRF disabled for /eureka/** so clients can register without a CSRF token
     *
     * Without disabling CSRF, every microservice trying to register gets a 403.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF only for Eureka registry endpoints
                // The dashboard endpoints still benefit from session-based CSRF
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/eureka/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .build();
    }
}