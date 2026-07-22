package org.example.application.services.chat;

import java.util.UUID;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.example.application.dto.chat.WsTokenResponseDTO;
import org.example.application.exception.chat.ChatException;
import org.example.domain.repository.UserRepository;
import org.example.utils.JwtAuthSupport;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Slf4j
@ApplicationScoped
public class ChatWsTokenService {

    private static final String WS_TOKEN_TYPE = "ws_token";
    private static final String WS_SCOPE = "chat:connect";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location", defaultValue = "privateKey.pem")
    String privateKeyLocation;

    @ConfigProperty(name = "chat.ws-token.ttl-seconds", defaultValue = "60")
    int ttlSeconds;

    @ConfigProperty(name = "chat.enabled", defaultValue = "true")
    boolean chatEnabled;

    @Inject
    JWTParser jwtParser;

    @Inject
    UserRepository userRepository;

    public WsTokenResponseDTO issueToken(UUID userId) {
        if (!chatEnabled) {
            throw ChatException.forbidden("Chat is disabled");
        }
        if (userRepository.findById(userId) == null) {
            throw ChatException.notFound("User not found");
        }

        try {
            PrivateKey privateKey = KeyUtils.readPrivateKey(privateKeyLocation);
            Instant expiresAt = Instant.now().plusSeconds(Math.max(30, ttlSeconds));

            String token =
                    Jwt.issuer(issuer)
                            .subject(String.valueOf(userId))
                            .claim("userId", userId.toString())
                            .claim("typ", WS_TOKEN_TYPE)
                            .claim("scope", WS_SCOPE)
                            .expiresAt(expiresAt)
                            .sign(privateKey);

            return WsTokenResponseDTO.builder()
                    .token(token)
                    .expiresAt(ISO.format(expiresAt))
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to issue ws token for userId={}", userId, e);
            throw new RuntimeException("Failed to issue WebSocket token", e);
        }
    }

    public UUID validateWsToken(String token) {
        try {
            JsonWebToken jwt = JwtAuthSupport.parseLegacy(jwtParser, token);
            Object typ = jwt.getClaim("typ");
            if (typ == null || !WS_TOKEN_TYPE.equals(typ.toString())) {
                throw ChatException.validation("Invalid WebSocket token type");
            }
            Object scope = jwt.getClaim("scope");
            if (scope == null || !WS_SCOPE.equals(scope.toString())) {
                throw ChatException.validation("Invalid WebSocket token scope");
            }
            Object userIdClaim = jwt.getClaim("userId");
            if (userIdClaim == null) {
                throw ChatException.validation("Invalid WebSocket token");
            }
            return UUID.fromString(userIdClaim.toString());
        } catch (ChatException e) {
            throw e;
        } catch (Exception e) {
            throw ChatException.validation("Invalid or expired WebSocket token");
        }
    }
}
