package org.example.application.services;

import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.impl.UserSyncService;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.auth.NeonAuthClaims;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;
import org.example.utils.AuthTokenException;
import org.example.utils.JwtAuthSupport;
import org.example.utils.RequestAuthHeaders;

/**
 * Sincroniza sessão Neon Auth (Google OAuth, email/senha) com o usuário interno no BD.
 * Valida o JWT EdDSA via {@link NeonAuthJwtVerifier} (JWKS Neon Auth).
 */
@Slf4j
@ApplicationScoped
public class AuthSessionService {

    private final NeonAuthJwtVerifier neonAuthJwtVerifier;
    private final JWTParser jwtParser;
    private final UserRepository userRepository;
    private final UserSyncService userSyncService;

    @Inject
    public AuthSessionService(
            NeonAuthJwtVerifier neonAuthJwtVerifier,
            JWTParser jwtParser,
            UserRepository userRepository,
            UserSyncService userSyncService) {
        this.neonAuthJwtVerifier = neonAuthJwtVerifier;
        this.jwtParser = jwtParser;
        this.userRepository = userRepository;
        this.userSyncService = userSyncService;
    }

    /**
     * @param authorizationHeader {@code Bearer <neon-auth-jwt>} ou use {@link RequestAuthHeaders#BAGGAGI_AUTHORIZATION}
     */
    @Transactional
    public UserResponseDTO syncFromBearer(
            String authorizationHeader, String baggagiAuthorizationHeader) {
        log.info("Starting session sync. Authorization header present: {}, Baggagi Authorization header present: {}",
                authorizationHeader != null, baggagiAuthorizationHeader != null);
                
        String token;
        try {
            token = RequestAuthHeaders.extractBearer(authorizationHeader, baggagiAuthorizationHeader);
            log.info("Token extracted successfully. Length: {}, First 10 chars: {}", token.length(), token.length() > 10 ? token.substring(0, 10) : token);
        } catch (AuthTokenException e) {
            log.error("Failed to extract bearer token: {}", e.getMessage());
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        if (neonAuthJwtVerifier.isConfigured()) {
            log.info("NeonAuthJwtVerifier is configured. Attempting to parse Neon Auth JWT...");
            try {
                NeonAuthClaims claims = JwtAuthSupport.parseNeonAuth(neonAuthJwtVerifier, token);
                log.info("Neon Auth JWT parsed successfully. Subject: {}", claims.authUserId());
                User user =
                        userSyncService.resolveOrCreateUser(
                                claims.authUserId(),
                                claims.email(),
                                claims.name(),
                                claims.resolvedProvider(),
                                claims.image());
                log.info(
                        "Session sync OK userId={} email={} authUserId={}",
                        user.id,
                        user.getEmail(),
                        user.getAuthUserId());
                return toResponse(user, token);
            } catch (AuthTokenException e) {
                log.warn(
                        "Session sync: Neon Auth JWT rejected — {} ({}) — trying legacy parser",
                        e.getCode(),
                        e.getMessage());
                e.printStackTrace();
            }
        } else {
            log.warn("NeonAuthJwtVerifier is NOT configured! Falling back to legacy parser immediately.");
        }

        log.info("Attempting to parse token using Legacy parser...");
        return syncFromLegacyToken(token);
    }

    private UserResponseDTO syncFromLegacyToken(String token) {
        JsonWebToken jwt;
        try {
            jwt = JwtAuthSupport.parseLegacy(jwtParser, token);
        } catch (AuthTokenException e) {
            log.warn("Session sync: legacy JWT rejected — {} ({})", e.getCode(), e.getMessage());
            throw new IllegalArgumentException("Invalid or expired token", e);
        }

        try {
            User user = resolveUserFromLegacyJwt(jwt);
            if (user == null) {
                throw new RuntimeException("User not found after session sync");
            }
            log.info(
                    "Session sync OK (legacy JWT) userId={} email={} authUserId={}",
                    user.id,
                    user.getEmail(),
                    user.getAuthUserId());
            return toResponse(user, token);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Session sync falhou por erro interno: {}", e.getMessage(), e);
            throw new RuntimeException("SESSION_SYNC_INTERNAL_ERROR: " + e.getMessage(), e);
        }
    }

    private User resolveUserFromLegacyJwt(JsonWebToken jwt) {
        Object customUserId = jwt.getClaim("custom:userId");
        if (customUserId != null && !customUserId.toString().isBlank()) {
            try {
                Long id = Long.valueOf(customUserId.toString().trim());
                User byId = userRepository.findById(id);
                if (byId != null) {
                    return byId;
                }
                log.warn("custom:userId={} not found in DB — falling back to JIT", id);
            } catch (NumberFormatException ignored) {
                log.warn("Invalid custom:userId claim: {}", customUserId);
            }
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new RuntimeException("Token does not contain sub claim");
        }

        String email = claimAsString(jwt.getClaim("email"));
        String name = claimAsString(jwt.getClaim("name"));
        String picture = claimAsString(jwt.getClaim("picture"));
        String provider = detectProviderFromLegacy(jwt);

        return userSyncService.resolveOrCreateUser(sub, email, name, provider, picture);
    }

    private static UserResponseDTO toResponse(User user, String token) {
        return UserResponseDTO.builder()
                .id(user.id)
                .email(user.getEmail())
                .username(user.getUsername())
                .fullname(user.getFullName())
                .token(token)
                .expiresIn(3600L)
                .build();
    }

    private static String claimAsString(Object claim) {
        if (claim == null) {
            return null;
        }
        String s = claim.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String detectProviderFromLegacy(JsonWebToken jwt) {
        Object identities = jwt.getClaim("identities");
        if (identities != null && identities.toString().toLowerCase().contains("google")) {
            return "google";
        }
        return "neon";
    }
}
