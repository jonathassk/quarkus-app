package org.example.utils;

import jakarta.ws.rs.core.HttpHeaders;

/**
 * API Gateway (HTTP API) pode ter JWT authorizer só no header {@code Authorization},
 * rejeitando tokens Neon Auth (EdDSA) antes da Lambda. O front envia o JWT em
 * {@value #BAGGAGI_AUTHORIZATION} para contornar até o authorizer ser removido/reconfigurado.
 */
public final class RequestAuthHeaders {

    public static final String BAGGAGI_AUTHORIZATION = "X-Baggagi-Authorization";

    private RequestAuthHeaders() {}

    public static String extractBearer(String authorizationHeader, String baggagiAuthorizationHeader) {
        String line = resolveBearerHeaderLine(authorizationHeader, baggagiAuthorizationHeader);
        if (line == null) {
            throw new AuthTokenException(
                    "MISSING_AUTH_HEADER", "Missing or invalid Authorization header");
        }
        return JwtAuthSupport.extractBearer(line);
    }

    public static String extractBearer(HttpHeaders headers) {
        if (headers == null) {
            throw new AuthTokenException(
                    "MISSING_AUTH_HEADER", "Missing or invalid Authorization header");
        }
        return extractBearer(
                headers.getHeaderString(HttpHeaders.AUTHORIZATION),
                headers.getHeaderString(BAGGAGI_AUTHORIZATION));
    }

    /** {@code Bearer …} ou {@code null} se nenhum header válido. */
    public static String resolveBearerHeaderLine(
            String authorizationHeader, String baggagiAuthorizationHeader) {
        if (baggagiAuthorizationHeader != null
                && baggagiAuthorizationHeader.startsWith("Bearer ")) {
            return baggagiAuthorizationHeader;
        }
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader;
        }
        return null;
    }
}
