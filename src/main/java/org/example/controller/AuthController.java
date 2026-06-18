package org.example.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.AuthSessionService;
import org.example.application.services.MagicLinkService;
import org.example.application.services.TokenService;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.utils.AuthTokenException;
import org.example.utils.RequestAuthHeaders;

import java.util.Map;

@Slf4j
@Tag(name = "Auth", description = "Autenticação, sincronização de sessão e Magic Links para guests")
@Path("/api/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;
    private final MagicLinkService magicLinkService;
    private final NeonAuthJwtVerifier neonAuthJwtVerifier;

    /**
     * Sincroniza usuário Neon Auth (Google OAuth, e-mail, etc.) com a tabela {@code users}.
     * Chamar após todo login no frontend (idempotente).
     */
    @POST
    @Path("/session-sync")
    @Operation(
        summary = "Sincronizar sessão Neon Auth",
        description = "Valida o JWT Neon Auth (Google OAuth, e-mail/senha) e provisiona o usuário na tabela `users` (JIT). " +
                      "Deve ser chamado após todo login no frontend — é idempotente. " +
                      "Se o e-mail já existir como GUEST, faz o upgrade automático para FREE."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Sessão sincronizada — retorna dados do usuário + token de sessão"),
        @APIResponse(responseCode = "401", description = "Token ausente, inválido ou expirado"),
        @APIResponse(responseCode = "500", description = "Erro interno durante a sincronização")
    })
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


    @GET
    @Path("/me")
    @Operation(
        summary = "Perfil do usuário autenticado",
        description = "Retorna os dados do usuário dono do token JWT atual."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Dados do usuário autenticado"),
        @APIResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @APIResponse(responseCode = "404", description = "Usuário não encontrado")
    })
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

    // =========================================================================
    // Magic Link — autenticação sem senha para usuários GUEST
    // =========================================================================

    /**
     * Solicita um Magic Link para um e-mail vinculado a uma viagem.
     *
     * <p>Corpo esperado: {@code { "email": "guest@email.com", "tripId": 42 }}
     *
     * <p>O JWT gerado expira em 15 minutos e é enviado para o e-mail do guest.
     * A resposta sempre retorna 200 para não vazar se o e-mail existe ou não.
     */
    @POST
    @Path("/magic-link/request")
    @Operation(
        summary = "Solicitar Magic Link (guest)",
        description = "Gera um JWT de Magic Link de 15 minutos para um e-mail vinculado a uma viagem de agência. " +
                      "Endpoint público — sem `Authorization` header. " +
                      "A resposta é sempre 200 para não revelar se o e-mail existe."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Requisição processada (link enviado ou e-mail não vinculado — mesmo body)"),
        @APIResponse(responseCode = "400", description = "email ou tripId ausentes"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response requestMagicLink(
        @RequestBody(description = "E-mail do guest e ID da viagem", required = true) MagicLinkRequestDTO body) {
        if (body == null || body.getEmail() == null || body.getTripId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("INVALID_REQUEST", "email e tripId são obrigatórios"))
                    .build();
        }
        try {
            String token = magicLinkService.generateMagicLinkToken(body.getEmail(), body.getTripId());

            // TODO: Integrar com provedor de e-mail (ex.: Resend, SendGrid, AWS SES).
            // O link a ser enviado deve ser: https://baggagi.com/magic?token=<token>
            // Por enquanto, logamos o token em nível INFO para testes locais.
            log.info("[MAGIC_LINK_DEV] token para email={} tripId={}: {}", body.getEmail(), body.getTripId(), token);

            // Retorna 200 mesmo em caso de e-mail não vinculado para evitar enumeração
            return Response.ok(Map.of("message", "Se este e-mail estiver vinculado à viagem, você receberá um link de acesso.")).build();
        } catch (AuthTokenException e) {
            // Loga internamente mas não expõe detalhes ao cliente
            log.warn("Magic link request rejected: email={} tripId={} reason={}", body.getEmail(), body.getTripId(), e.getMessage());
            return Response.ok(Map.of("message", "Se este e-mail estiver vinculado à viagem, você receberá um link de acesso.")).build();
        } catch (Exception e) {
            log.error("Magic link request failed: email={} tripId={}", body.getEmail(), body.getTripId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorBody("INTERNAL_ERROR", "Erro interno ao processar a solicitação"))
                    .build();
        }
    }

    /**
     * Verifica um Magic Link JWT e retorna um access token de sessão.
     *
     * <p>Corpo esperado: {@code { "token": "<magic_link_jwt>" }}
     *
     * <p>Em caso de sucesso, retorna:
     * {@code { "accessToken": "<session_jwt>", "userId": 42, "tripId": 7 }}
     */
    @POST
    @Path("/magic-link/verify")
    @Operation(
        summary = "Verificar Magic Link e obter session token (guest)",
        description = "Valida o token JWT do Magic Link recebido via e-mail e retorna um access token de sessão " +
                      "normal (válido 7 dias), além do `userId` e `tripId`. " +
                      "Endpoint público — sem `Authorization` header. " +
                      "Use o `accessToken` retornado como `Bearer` em todos os endpoints protegidos."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Token de sessão emitido com sucesso"),
        @APIResponse(responseCode = "400", description = "Campo `token` ausente"),
        @APIResponse(responseCode = "401", description = "Token expirado (TOKEN_EXPIRED), inválido (INVALID_TOKEN), ou de tipo errado (INVALID_TOKEN_TYPE)"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response verifyMagicLink(
        @RequestBody(description = "JWT do magic link recebido via e-mail", required = true) MagicLinkVerifyRequestDTO body) {
        if (body == null || body.getToken() == null || body.getToken().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("INVALID_REQUEST", "token é obrigatório"))
                    .build();
        }
        try {
            MagicLinkService.MagicLinkVerifyResult result =
                    magicLinkService.verifyMagicLinkToken(body.getToken());

            return Response.ok(Map.of(
                    "accessToken", result.accessToken(),
                    "userId", result.userId(),
                    "tripId", result.tripId()
            )).build();
        } catch (AuthTokenException e) {
            log.warn("Magic link verify failed: code={} message={}", e.getCode(), e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(errorBody(e.getCode(), e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Magic link verify error", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorBody("INTERNAL_ERROR", "Erro interno"))
                    .build();
        }
    }

    @GET
    @Path("/neon-status")
    @Operation(
        summary = "Verificar status de configuração do Neon Auth",
        description = "Retorna se o validador Neon Auth está configurado corretamente."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Retorna se o verificador está ativo")
    })
    public Response getNeonStatus() {
        boolean configured = neonAuthJwtVerifier.isConfigured();
        return Response.ok(Map.of("neonVerifierConfigured", configured)).build();
    }

    // -------------------------------------------------------------------------
    // DTOs internos dos endpoints de magic-link
    // -------------------------------------------------------------------------

    @Data
    public static class MagicLinkRequestDTO {
        private String email;
        private Long tripId;
    }

    @Data
    public static class MagicLinkVerifyRequestDTO {
        private String token;
    }

    private static Map<String, String> errorBody(String code, String message) {
        return Map.of("code", code, "message", message != null ? message : "");
    }
}
