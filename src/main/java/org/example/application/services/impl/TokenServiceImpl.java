package org.example.application.services.impl;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.example.application.services.TokenService;
import org.example.domain.entity.User;
import org.example.infrastructure.auth.NeonAuthClaims;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;
import org.example.utils.AuthTokenException;
import org.example.utils.JwtAuthSupport;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ApplicationScoped
public class TokenServiceImpl implements TokenService {

    private static final long JIT_CACHE_TTL_MS = Duration.ofMinutes(30).toMillis();

    private record CachedUserId(String userId, long expiresAtMs) {}

    private final ConcurrentHashMap<String, CachedUserId> jitCacheBySub = new ConcurrentHashMap<>();

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location", defaultValue = "privateKey.pem")
    String privateKeyLocation;

    @Inject
    NeonAuthJwtVerifier neonAuthJwtVerifier;

    @Inject
    JWTParser jwtParser;

    @Inject
    UserSyncService userSyncService;

    @Override
    public String generateToken(User user, String password) throws GeneralSecurityException, IOException {
        try {
            return Jwt.issuer(issuer)
                    .upn(user.getEmail())
                    .groups(Collections.singleton("USER"))
                    .claim("userId", user.id)
                    .claim("userType", user.getUserType() != null ? user.getUserType().name() : "FREE")
                    .expiresIn(Duration.ofDays(7))
                    .sign(KeyUtils.readPrivateKey(privateKeyLocation));
        } catch (Exception e) {
            log.error("Failed to generate access token: userId={}", user.id, e);
            throw e;
        }
    }

    @Override
    public String generateRefreshToken(User user) throws GeneralSecurityException, IOException {
        try {
            return Jwt.issuer(issuer)
                    .upn(user.getEmail())
                    .claim("userId", user.id)
                    .claim("typ", "refresh")
                    .expiresIn(Duration.ofDays(30))
                    .sign(KeyUtils.readPrivateKey(privateKeyLocation));
        } catch (Exception e) {
            log.error("Failed to generate refresh token: userId={}", user.id, e);
            throw e;
        }
    }

    @Override
    public String validateToken(String token) {
        if (neonAuthJwtVerifier.isConfigured()) {
            try {
                NeonAuthClaims claims = JwtAuthSupport.parseNeonAuth(neonAuthJwtVerifier, token);
                return resolveUserIdForNeonSub(claims);
            } catch (AuthTokenException neonEx) {
                log.warn(
                        "Neon Auth JWT validation failed ({}): {} — trying legacy parser",
                        neonEx.getCode(),
                        neonEx.getMessage());
            }
        }

        try {
            JsonWebToken jwt = JwtAuthSupport.parseLegacy(jwtParser, token);

            // 1. Token legado com claim "userId" numérico (emitido localmente antes da migração Neon Auth)
            Object legacyUserId = jwt.getClaim("userId");
            if (legacyUserId != null && !legacyUserId.toString().isBlank()) {
                String lid = legacyUserId.toString().trim();
                if (lid.matches("\\d+")) {
                    return lid;
                }
            }

            // 3. JIT fallback — cached per legacy sub to avoid DB + warn on every request
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return resolveUserIdForSub(jwt, sub);
            }

            log.warn("Token validation failed: no userId, legacyUserId, or sub claim found");
            throw new RuntimeException("Token does not contain userId claim");

        } catch (RuntimeException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (detail.toLowerCase().contains("expired")) {
                log.warn("Token validation failed: expired ({})", detail);
                throw new RuntimeException("Token expired", e);
            }
            log.warn("Token validation failed: {}", detail);
            throw new RuntimeException("Invalid token", e);
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Token validation failed: {}", detail);
            throw new RuntimeException("Invalid token", e);
        }
    }

    private String resolveUserIdForNeonSub(NeonAuthClaims claims) {
        String sub = claims.authUserId();
        long now = System.currentTimeMillis();
        CachedUserId cached = jitCacheBySub.get(sub);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.userId();
        }

        String userId =
                userSyncService.resolveOrCreate(
                        sub, claims.email(), claims.name(), "neon", claims.image());
        jitCacheBySub.put(sub, new CachedUserId(userId, now + JIT_CACHE_TTL_MS));
        return userId;
    }

    private String resolveUserIdForSub(JsonWebToken jwt, String sub) {
        long now = System.currentTimeMillis();
        CachedUserId cached = jitCacheBySub.get(sub);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.userId();
        }

        String email = claimAsString(jwt.getClaim("email"));
        String name = claimAsString(jwt.getClaim("name"));
        String userId = userSyncService.resolveOrCreate(sub, email, name);
        jitCacheBySub.put(sub, new CachedUserId(userId, now + JIT_CACHE_TTL_MS));
        if (cached == null) {
            log.info(
                    "Token missing custom:userId — resolved via JIT for sub={} (call POST /auth/session-sync to enrich claims)",
                    sub);
        } else {
            log.debug("JIT cache refreshed for sub={}", sub);
        }
        return userId;
    }

    private static String claimAsString(Object claim) {
        if (claim == null) {
            return null;
        }
        String s = claim.toString().trim();
        return s.isEmpty() ? null : s;
    }

    @Override
    public String refreshToken(String token) {
        return "";
    }
}
