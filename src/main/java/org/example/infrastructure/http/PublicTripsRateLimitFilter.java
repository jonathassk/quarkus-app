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
 * Rate limit por IP no endpoint público de viagem ({@code GET /api/v1/public/trips/{code}}).
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class PublicTripsRateLimitFilter implements ContainerRequestFilter {

    @Inject
    ChatRateLimiter rateLimiter;

    @ConfigProperty(name = "public.trips.max-requests-per-min", defaultValue = "60")
    int maxRequestsPerMin;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (!normalized.startsWith("/api/v1/public/trips/")
                && !normalized.startsWith("api/v1/public/trips/")) {
            return;
        }
        if (!"GET".equals(requestContext.getMethod())) {
            return;
        }

        String ip = clientIp(requestContext);
        String key = "public_trip:" + ip;
        if (!rateLimiter.tryAcquire(key, maxRequestsPerMin, Duration.ofMinutes(1))) {
            int retryAfter = rateLimiter.retryAfterSeconds(key, Duration.ofMinutes(1));
            log.warn("Public trip rate limit exceeded ip={} path={}", ip, normalized);
            requestContext.abortWith(
                    Response.status(429)
                            .header("Retry-After", retryAfter)
                            .entity(
                                    ApiErrorBody.builder()
                                            .code("RATE_LIMITED")
                                            .message("Too many requests. Try again later.")
                                            .build())
                            .build());
        }
    }

    private static String clientIp(ContainerRequestContext ctx) {
        String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = ctx.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (ctx.getUriInfo() != null && ctx.getUriInfo().getRequestUri() != null) {
            return "unknown";
        }
        return "unknown";
    }
}
