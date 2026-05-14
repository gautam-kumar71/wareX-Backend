package com.inventory.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter — runs on EVERY request through the gateway.
 *
 * Logs:
 *   → [REQ ] method + path + X-User-Id (if present)
 *   ← [RESP] status + duration in ms
 *
 * Order: -2 (runs very early, wraps everything else)
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String userId = request.getHeaders().getFirst("X-User-Id");
        String userInfo = userId != null ? " [userId=" + userId + "]" : "";

        log.debug("→ [REQ ] {} {}{} from {}",
                request.getMethod(),
                request.getPath(),
                userInfo,
                request.getRemoteAddress());

        return chain.filter(exchange)
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;

                    log.debug("← [RESP] {} {} → {} ({}ms)",
                            request.getMethod(),
                            request.getPath(),
                            statusCode,
                            duration);
                });
    }

    @Override
    public int getOrder() {
        return -2;  // Run before JWT filter (which is order -1 by default)
    }
}