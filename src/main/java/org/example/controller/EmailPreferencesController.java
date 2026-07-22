package org.example.controller;

import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.email.UserEmailPreferencesDTO;
import org.example.application.services.TokenService;
import org.example.application.services.email.EmailPreferencesService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

@Slf4j
@Tag(name = "Email Preferences", description = "Preferências de notificação por e-mail")
@Path("/api/v1/users/me/email-preferences")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class EmailPreferencesController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final EmailPreferencesService emailPreferencesService;

    @GET
    @Transactional
    @Operation(summary = "Obter preferências de e-mail")
    public Response get(@Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(emailPreferencesService.getPreferences(userId)).build());
    }

    @PATCH
    @Transactional
    @Operation(summary = "Atualizar preferências de e-mail")
    public Response update(UserEmailPreferencesDTO body, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> Response.ok(emailPreferencesService.updatePreferences(userId, body)).build());
    }

    private Response withAuth(HttpHeaders headers, java.util.function.Function<UUID, Response> action) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }
        return action.apply(userId.get());
    }

    private Optional<UUID> resolveAuthenticatedUserId(HttpHeaders headers) {
        String bearerLine =
                headers != null
                        ? RequestAuthHeaders.resolveBearerHeaderLine(
                                headers.getHeaderString(HttpHeaders.AUTHORIZATION),
                                headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION))
                        : null;
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
            log.warn("Email preferences auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
