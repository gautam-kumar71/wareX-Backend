package com.inventory.purchaseorder.config;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign configuration.
 *
 * The RequestInterceptor forwards the Authorization header from the
 * incoming request to all outgoing Feign calls. This means when a
 * client calls the Purchase Order Service with a JWT, that JWT is
 * automatically forwarded to Warehouse Service and Supplier Service —
 * so those services can also enforce role-based access.
 */
@Configuration
@EnableFeignClients(basePackages = "com.inventory.purchaseorder.feign")
public class FeignConfig {

    @Bean
    public RequestInterceptor authHeaderForwardingInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs != null) {
                String authHeader = attrs.getRequest().getHeader("Authorization");
                String userId     = attrs.getRequest().getHeader("X-User-Id");
                String userRole   = attrs.getRequest().getHeader("X-User-Role");

                if (authHeader != null) {
                    requestTemplate.header("Authorization", authHeader);
                }
                if (userId != null) {
                    requestTemplate.header("X-User-Id", userId);
                }
                if (userRole != null) {
                    requestTemplate.header("X-User-Role", userRole);
                }
            }
        };
    }
}