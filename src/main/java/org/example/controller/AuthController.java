package org.example.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.AuthSessionService;
import org.example.application.services.TokenService;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;
import org.example.utils.AuthTokenException;
import org.example.utils.JwtAuthSupport;
import org.example.utils.RequestAuthHeaders;

import com.nimbusds.jwt.SignedJWT;

@Slf4j
@Path("/api/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;
    private final NeonAuthJwtVerifier neonAuthJwtVerifier;

    /**
     * Sincroniza usuário Neon Auth (Google OAuth, e-mail, etc.) com a tabela {@code users}.
     * Chamar após todo login no frontend (idempotente).
     */
    /**
     * Diagnóstico público: confirma se a Lambda valida JWT Neon (sem expor segredos).
     * Opcional: {@code Authorization: Bearer <jwt>} retorna iss/sub do token e se a API aceitaria.
     */
    @GET
    @Path("/neon-status")
    public Response neonAuthStatus(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam(RequestAuthHeaders.BAGGAGI_AUTHORIZATION) String baggagiAuthorizationHeader) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("neonVerifierConfigured", neonAuthJwtVerifier.isConfigured());
        body.put("expectedIssuer", neonAuthJwtVerifier.getExpectedIssuer());
        body.put("jwksUrl", neonAuthJwtVerifier.getJwksUrl());

        String bearerLine =
                RequestAuthHeaders.resolveBearerHeaderLine(
                        authorizationHeader, baggagiAuthorizationHeader);
        if (bearerLine != null) {
            String raw = bearerLine.substring(7).trim();
            try {
                var claims = SignedJWT.parse(raw).getJWTClaimsSet();
                body.put("tokenIssuer", claims.getIssuer());
                body.put("tokenSubject", claims.getSubject());
                body.put("tokenAudience", claims.getAudience());
                body.put("tokenExpiresAt", claims.getExpirationTime());
            } catch (Exception e) {
                body.put("tokenParseError", e.getMessage());
            }
            try {
                JwtAuthSupport.parseNeonAuth(neonAuthJwtVerifier, raw);
                body.put("tokenValid", true);
            } catch (AuthTokenException e) {
                body.put("tokenValid", false);
                body.put("tokenErrorCode", e.getCode());
                body.put("tokenErrorMessage", e.getMessage());
            }
        }

        return Response.ok(body).build();
    }

    @POST
    @Path("/session-sync")
    public Response sessionSync(
            @HeaderParam("Authorization") String authorizationHeader,
            @HeaderParam(RequestAuthHeaders.BAGGAGI_AUTHORIZATION) String baggagiAuthorizationHeader) {
        try {
            UserResponseDTO body =
                    authSessionService.syncFromBearer(authorizationHeader, baggagiAuthorizationHeader);
            return Response.ok(body).build();
        } catch (IllegalArgumentException e) {
            String code = "INVALID_TOKEN";
            String message = e.getMessage();
            if (e.getCause() instanceof AuthTokenException ate) {
                code = ate.getCode();
                message = ate.getMessage();
            }
            log.warn("POST /auth/session-sync 401: {} ({})", message, code);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(errorBody(code, message))
                    .build();
        } catch (Exception e) {
            // Falha interna (BD, IAM, etc.) → 500, não 401
            // O front não deve fazer refresh de token por causa de erro de servidor
            log.error("POST /auth/session-sync 500: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorBody("SESSION_SYNC_ERROR", "Internal error during session sync"))
                    .build();
        }
    }

    private static java.util.Map<String, String> errorBody(String code, String message) {
        return java.util.Map.of("code", code, "message", message != null ? message : "");
    }

    @GET
    @Path("/me")
    public Response getAuthenticatedUser(@HeaderParam("Authorization") String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("GET /auth/me rejected: missing or invalid Authorization header");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Missing or invalid Authorization header")
                    .build();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        try {
            String userIdStr = tokenService.validateToken(token);
            Long userId = Long.valueOf(userIdStr);

            User user = userRepository.findById(userId);
            if (user == null) {
                log.warn("GET /auth/me rejected: user not found for userId={}", userId);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("User not found")
                        .build();
            }

            UserResponseDTO response = UserResponseDTO.builder()
                    .token(token)
                    .refreshToken(null)
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .fullname(user.getFullName())
                    .id(user.id)
                    .expiresIn(null)
                    .build();

            return Response.ok(response).build();
        } catch (Exception e) {
            log.warn("GET /auth/me rejected: invalid or expired token ({})", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }
    }
}
