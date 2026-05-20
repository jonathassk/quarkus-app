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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;

@Slf4j
@ApplicationScoped
public class TokenServiceImpl implements TokenService {
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location")
    String privateKeyLocation;

    @Inject
    JWTParser jwtParser;

    @Override
    public String generateToken(User user, String password) throws GeneralSecurityException, IOException {
        try {
            return Jwt.issuer(issuer)
                    .upn(user.getEmail())
                    .groups(Collections.singleton("USER"))
                    .claim("userId", user.id)
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
        try {
            JsonWebToken jwt = jwtParser.parse(token);

            // Cognito tokens carry the DB user ID as custom:userId
            Object userId = jwt.getClaim("custom:userId");
            if (userId != null && !userId.toString().isBlank()) {
                return userId.toString();
            }

            // Fallback: legacy local JWT used the userId claim
            Object legacyUserId = jwt.getClaim("userId");
            if (legacyUserId != null) {
                return legacyUserId.toString();
            }

            log.warn("Token validation failed: missing userId claim");
            throw new RuntimeException("Token does not contain userId claim");
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            throw new RuntimeException("Invalid token", e);
        }
    }

    @Override
    public String refreshToken(String token) {
        return "";
    }
}
