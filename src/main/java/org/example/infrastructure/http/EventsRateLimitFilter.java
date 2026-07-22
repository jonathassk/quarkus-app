package org.example.infrastructure.http;

import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.services.TokenService;
import org.example.application.services.chat.ChatRateLimiter;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class EventsRateLimitFilter implements ContainerRequestFilter {

    private static final Pattern EVENT_ID = Pattern.compile("^/api/v1/events/([0-9a-fA-F-]{36})");

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    ChatRateLimiter rateLimiter;

    @ConfigProperty(name = "events.enabled", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty(name = "events.max-posts-per-min", defaultValue = "10")
    int maxPostsPerMin;

    @ConfigProperty(name = "events.max-invites-per-hour", defaultValue = "50")
    int maxInvitesPerHour;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!eventsEnabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("api/v1/events") && !path.startsWith("/api/v1/events")) {
            return;
        }

        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String method = requestContext.getMethod();

        RateLimitRule rule = resolveRule(normalizedPath, method);
        if (rule == null) {
            return;
        }

        Optional<UUID> userId =
                resolveAuthenticatedUserId(
                        requestContext.getHeaderString(HttpHeaders.AUTHORIZATION),
                        requestContext.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION));
        if (userId.isEmpty()) {
            return;
        }

        String key = rule.name + ":" + userId.get() + ":" + rule.suffix;
        if (!rateLimiter.tryAcquire(key, rule.maxRequests, rule.window)) {
            int retryAfter = rateLimiter.retryAfterSeconds(key, rule.window);
            log.warn("Events rate limit exceeded userId={} rule={} path={}", userId.get(), rule.name, normalizedPath);
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

    private RateLimitRule resolveRule(String path, String method) {
        if ("POST".equals(method) && path.endsWith("/posts")) {
            Matcher matcher = EVENT_ID.matcher(path);
            String eventId = matcher.find() ? matcher.group(1) : "unknown";
            return new RateLimitRule("event_post", maxPostsPerMin, Duration.ofMinutes(1), eventId);
        }
        if ("POST".equals(method) && path.endsWith("/invites")) {
            Matcher matcher = EVENT_ID.matcher(path);
            String eventId = matcher.find() ? matcher.group(1) : "unknown";
            return new RateLimitRule("event_invite", maxInvitesPerHour, Duration.ofHours(1), eventId);
        }
        return null;
    }

    private Optional<UUID> resolveAuthenticatedUserId(String authorization, String baggagiAuthorization) {
        String bearerLine = RequestAuthHeaders.resolveBearerHeaderLine(authorization, baggagiAuthorization);
        if (bearerLine == null) {
            return Optional.empty();
        }
        try {
            String token = bearerLine.substring("Bearer ".length()).trim();
            UUID userId = UUID.fromString(tokenService.validateToken(token));
            if (userRepository.findById(userId) == null) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private record RateLimitRule(String name, int maxRequests, Duration window, String suffix) {}
}
