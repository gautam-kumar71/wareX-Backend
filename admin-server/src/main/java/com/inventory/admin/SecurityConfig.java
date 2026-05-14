package com.inventory.admin;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminServerProperties adminServer;

    public SecurityConfig(AdminServerProperties adminServer) {
        this.adminServer = adminServer;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl(this.adminServer.path("/applications"));

        return http
                .authorizeHttpRequests(auth -> auth
                        // Public: static assets, login page, actuator health
                        .requestMatchers(
                                new AntPathRequestMatcher(this.adminServer.path("/assets/**")),
                                new AntPathRequestMatcher(this.adminServer.path("/sba-settings.js")),
                                new AntPathRequestMatcher(this.adminServer.path("/ui-extensions/**")),
                                new AntPathRequestMatcher(this.adminServer.path("/variables.css")),
                                new AntPathRequestMatcher(this.adminServer.path("/actuator/info")),
                                new AntPathRequestMatcher(this.adminServer.path("/actuator/health")),
                                new AntPathRequestMatcher(this.adminServer.path("/login")),
                                new AntPathRequestMatcher("/actuator/health"),
                                new AntPathRequestMatcher("/actuator/info")
                        ).permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Login form
                .formLogin(form -> form
                        .loginPage(this.adminServer.path("/login"))
                        .successHandler(successHandler)
                )
                // Logout
                .logout(logout -> logout
                        .logoutUrl(this.adminServer.path("/logout"))
                )
                // HTTP Basic for programmatic access (e.g. from CI/CD pipelines)
                .httpBasic(Customizer.withDefaults())
                // CSRF: use cookie-based token (compatible with the Admin UI SPA)
                // Disable CSRF for actuator endpoints so microservices can POST heartbeats
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher(this.adminServer.path("/instances"), "POST"),
                                new AntPathRequestMatcher(this.adminServer.path("/instances/*"), "DELETE"),
                                new AntPathRequestMatcher(this.adminServer.path("/actuator/**")),
                                new AntPathRequestMatcher("/actuator/**")
                        )
                )
                .build();
    }
}