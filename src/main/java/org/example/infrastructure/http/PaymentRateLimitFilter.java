package org.example.infrastructure.http;

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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Rate limit em endpoints que criam cobrança / reconciliação Stripe.
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class PaymentRateLimitFilter implements ContainerRequestFilter {

    private static final Pattern PUBLIC_PROPOSAL_CHECKOUT =
            Pattern.compile("^/api/v1/public/proposals/[^/]+/checkout$");

    @Inject
    ChatRateLimiter rateLimiter;

    @Inject
    TokenService tokenService;

    @Inject
    UserRepository userRepository;

    @ConfigProperty(name = "payment.rate-limit.checkout-per-min", defaultValue = "10")
    int checkoutPerMin;

    @ConfigProperty(name = "payment.rate-limit.reconcile-per-min", defaultValue = "20")
    int reconcilePerMin;

    @ConfigProperty(name = "payment.rate-limit.public-checkout-per-min", defaultValue = "5")
    int publicCheckoutPerMin;

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

        String subject = resolveSubject(requestContext, rule.useUserIfPresent);
        String key = rule.name + ":" + subject;
        if (!rateLimiter.tryAcquire(key, rule.maxRequests, rule.window)) {
            int retryAfter = rateLimiter.retryAfterSeconds(key, rule.window);
            log.warn("Payment rate limit exceeded subject={} rule={} path={}", subject, rule.name, normalized);
            requestContext.abortWith(
                    Response.status(429)
                            .header("Retry-After", retryAfter)
                            .entity(
                                    ApiErrorBody.builder()
                                            .code("RATE_LIMITED")
                                            .message("Too many payment requests. Try again later.")
                                            .build())
                            .build());
        }
    }

    private RateLimitRule resolveRule(String path) {
        if (path.equals("/api/v1/payments/checkout-session")) {
            return new RateLimitRule("payment_checkout", checkoutPerMin, Duration.ofMinutes(1), true);
        }
        if (path.equals("/api/v1/payments/reconcile")) {
            return new RateLimitRule("payment_reconcile", reconcilePerMin, Duration.ofMinutes(1), true);
        }
        if (PUBLIC_PROPOSAL_CHECKOUT.matcher(path).matches()) {
            return new RateLimitRule(
                    "public_proposal_checkout", publicCheckoutPerMin, Duration.ofMinutes(1), false);
        }
        return null;
    }

    private String resolveSubject(ContainerRequestContext ctx, boolean preferUser) {
        if (preferUser) {
            Optional<UUID> userId =
                    resolveAuthenticatedUserId(
                            ctx.getHeaderString(HttpHeaders.AUTHORIZATION),
                            ctx.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION));
            if (userId.isPresent()) {
                return "user:" + userId.get();
            }
        }
        return "ip:" + ClientIp.from(ctx);
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

    private record RateLimitRule(String name, int maxRequests, Duration window, boolean useUserIfPresent) {}
}
