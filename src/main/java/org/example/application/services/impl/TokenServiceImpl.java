package org.example.application.services.impl;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.services.TokenService;
import org.example.domain.entity.User;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;

@ApplicationScoped
public class TokenServiceImpl implements TokenService {
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location")
    String privateKeyLocation;

    @Override
    public String generateToken(User user, String password) throws GeneralSecurityException, IOException {
        return Jwt.issuer(issuer)
                .upn(user.getEmail())
                .groups(Collections.singleton("USER"))
                .claim("userId", user.id)
                .expiresIn(Duration.ofHours(12))
                .sign(KeyUtils.readPrivateKey(privateKeyLocation));
    }

    @Override
    public String validateToken(String token) {
        try {
            return token; // Token is valid
        } catch (Exception e) {
            throw new RuntimeException("Invalid token", e); // Handle invalid token
        }
    }

    @Override
    public String refreshToken(String token) {
        return "";
    }
}
