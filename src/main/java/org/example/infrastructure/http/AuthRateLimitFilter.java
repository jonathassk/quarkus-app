package org.example.infrastructure.http;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.services.chat.ChatRateLimiter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limit por IP em login legado e magic-link (brute-force / enumeração).
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthRateLimitFilter implements ContainerRequestFilter {

    @Inject
    ChatRateLimiter rateLimiter;

    @ConfigProperty(name = "auth.rate-limit.login-per-min", defaultValue = "10")
    int loginPerMin;

    @ConfigProperty(name = "auth.rate-limit.magic-link-request-per-hour", defaultValue = "5")
    int magicLinkRequestPerHour;

    @ConfigProperty(name = "auth.rate-limit.magic-link-verify-per-min", defaultValue = "20")
    int magicLinkVerifyPerMin;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!"POST".equals(requestContext.getMethod())) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        String normalized = path.startsWith("/") ? path : "/" + path;

        RateLimitRule rule = resolveRule(normalized);
        if (rule == null) {
            return;
        }

        String ip = ClientIp.from(requestContext);
        String key = rule.name + ":" + ip;
        if (!rateLimiter.tryAcquire(key, rule.maxRequests, rule.window)) {
            int retryAfter = rateLimiter.retryAfterSeconds(key, rule.window);
            log.warn("Auth rate limit exceeded ip={} rule={} path={}", ip, rule.name, normalized);
            requestContext.abortWith(
                    Response.status(429)
                            .header("Retry-After", retryAfter)
                            .entity(
                                    ApiErrorBody.builder()
                                            .code("RATE_LIMITED")
                                            .message("Too many auth attempts. Try again later.")
                                            .build())
                            .build());
        }
    }

    private RateLimitRule resolveRule(String path) {
        if (path.equals("/api/v1/users/login")) {
            return new RateLimitRule("login", loginPerMin, Duration.ofMinutes(1));
        }
        if (path.equals("/api/v1/auth/magic-link/request")) {
            return new RateLimitRule(
                    "magic_link_request", magicLinkRequestPerHour, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/auth/magic-link/verify")) {
            return new RateLimitRule(
                    "magic_link_verify", magicLinkVerifyPerMin, Duration.ofMinutes(1));
        }
        return null;
    }

    private record RateLimitRule(String name, int maxRequests, Duration window) {}
}
