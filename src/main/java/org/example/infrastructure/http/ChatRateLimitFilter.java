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
import java.util.regex.Pattern;

@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class ChatRateLimitFilter implements ContainerRequestFilter {

    private static final Pattern MESSAGES_POST =
            Pattern.compile("^/api/v1/chat/conversations/[0-9a-fA-F-]{36}/messages$");
    private static final Pattern MESSAGES_GET =
            Pattern.compile("^/api/v1/chat/conversations/[0-9a-fA-F-]{36}/messages$");
    private static final Pattern DIRECT_POST = Pattern.compile("^/api/v1/chat/direct$");
    private static final Pattern WS_TOKEN_GET = Pattern.compile("^/api/v1/chat/ws-token$");

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @Inject
    ChatRateLimiter rateLimiter;

    @ConfigProperty(name = "chat.enabled", defaultValue = "true")
    boolean chatEnabled;

    @ConfigProperty(name = "chat.rate-limit.messages-per-min", defaultValue = "30")
    int messagesPerMin;

    @ConfigProperty(name = "chat.rate-limit.direct-per-hour", defaultValue = "10")
    int directPerHour;

    @ConfigProperty(name = "chat.rate-limit.get-messages-per-min", defaultValue = "60")
    int getMessagesPerMin;

    @ConfigProperty(name = "chat.rate-limit.ws-connect-per-min", defaultValue = "10")
    int wsConnectPerMin;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!chatEnabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("api/v1/chat")) {
            return;
        }

        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String method = requestContext.getMethod();

        RateLimitRule rule = resolveRule(normalizedPath, method);
        if (rule == null) {
            return;
        }

        Optional<UUID> userId = resolveAuthenticatedUserId(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION),
                requestContext.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION));
        if (userId.isEmpty()) {
            return;
        }

        String key = rule.name + ":" + userId.get();
        if (!rateLimiter.tryAcquire(key, rule.maxRequests, rule.window)) {
            int retryAfter = rateLimiter.retryAfterSeconds(key, rule.window);
            log.warn(
                    "Chat rate limit exceeded userId={} rule={} path={}",
                    userId.get(),
                    rule.name,
                    normalizedPath);
            requestContext.abortWith(
                    Response.status(429)
                            .header("Retry-After", retryAfter)
                            .entity(
                                    ApiErrorBody.builder()
                                            .code("RATE_LIMIT_EXCEEDED")
                                            .message("Too many chat requests. Try again later.")
                                            .build())
                            .build());
        }
    }

    private RateLimitRule resolveRule(String path, String method) {
        if ("POST".equals(method) && MESSAGES_POST.matcher(path).matches()) {
            return new RateLimitRule("post_message", messagesPerMin, Duration.ofMinutes(1));
        }
        if ("GET".equals(method) && MESSAGES_GET.matcher(path).matches()) {
            return new RateLimitRule("get_messages", getMessagesPerMin, Duration.ofMinutes(1));
        }
        if ("POST".equals(method) && DIRECT_POST.matcher(path).matches()) {
            return new RateLimitRule("post_direct", directPerHour, Duration.ofHours(1));
        }
        if ("GET".equals(method) && WS_TOKEN_GET.matcher(path).matches()) {
            return new RateLimitRule("ws_connect", wsConnectPerMin, Duration.ofMinutes(1));
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

    private record RateLimitRule(String name, int maxRequests, Duration window) {}
}
