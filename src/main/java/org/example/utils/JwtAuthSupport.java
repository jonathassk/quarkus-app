package org.example.utils;

import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.example.infrastructure.auth.NeonAuthClaims;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;

/**
 * Parse de JWT Neon Auth e tokens legados (email/senha) com mensagens previsíveis.
 */
public final class JwtAuthSupport {

    private JwtAuthSupport() {}

    public static String extractBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthTokenException(
                    "MISSING_AUTH_HEADER", "Missing or invalid Authorization header");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new AuthTokenException("MISSING_TOKEN", "Bearer token is empty");
        }
        return token;
    }

    public static String extractBearer(HttpHeaders headers) {
        return RequestAuthHeaders.extractBearer(headers);
    }

    public static NeonAuthClaims parseNeonAuth(NeonAuthJwtVerifier verifier, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthTokenException("MISSING_TOKEN", "Token is required");
        }
        return verifier.verify(rawToken.trim());
    }

    public static JsonWebToken parseLegacy(JWTParser jwtParser, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthTokenException("MISSING_TOKEN", "Token is required");
        }
        try {
            return jwtParser.parse(rawToken.trim());
        } catch (Exception e) {
            throw mapParseFailure(e);
        }
    }

    private static AuthTokenException mapParseFailure(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        Throwable cause = e.getCause();
        String causeMsg = cause != null && cause.getMessage() != null
                ? cause.getMessage().toLowerCase()
                : "";

        if (msg.contains("expired") || causeMsg.contains("expired")) {
            return new AuthTokenException(
                    "TOKEN_EXPIRED", "Token expired — sign in again", e);
        }
        if (msg.contains("signature") || causeMsg.contains("signature")) {
            return new AuthTokenException("TOKEN_INVALID", "Invalid token signature", e);
        }
        if (msg.contains("issuer") || causeMsg.contains("issuer")) {
            return new AuthTokenException(
                    "TOKEN_ISSUER_MISMATCH", "Token issuer does not match API configuration", e);
        }
        return new AuthTokenException("TOKEN_INVALID", "Invalid or malformed token", e);
    }
}
