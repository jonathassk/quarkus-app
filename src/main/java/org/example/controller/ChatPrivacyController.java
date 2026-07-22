package org.example.controller;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.chat.ChatEligibilityDTO;
import org.example.application.dto.chat.UserPrivacySettingsDTO;
import org.example.application.services.TokenService;
import org.example.application.services.chat.DirectChatService;
import org.example.application.services.chat.PrivacyService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;

@Slf4j
@Tag(name = "Chat Privacy", description = "Privacidade e elegibilidade de DM")
@Path("/api/v1/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ChatPrivacyController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final DirectChatService directChatService;
    private final PrivacyService privacyService;

    @GET
    @Path("/{id}/chat-eligibility")
    @Transactional
    @Operation(summary = "Verificar elegibilidade para DM")
    public Response getChatEligibility(@PathParam("id") UUID targetUserId, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    ChatEligibilityDTO eligibility = directChatService.getEligibility(userId, targetUserId);
                    return Response.ok(eligibility).build();
                });
    }

    @GET
    @Path("/me/privacy")
    @Transactional
    @Operation(summary = "Obter configurações de privacidade do chat")
    public Response getPrivacy(@Context HttpHeaders headers) {
        return withAuth(
                headers, userId -> Response.ok(privacyService.getPrivacy(userId)).build());
    }

    @PATCH
    @Path("/me/privacy")
    @Transactional
    @Operation(summary = "Atualizar configurações de privacidade do chat")
    public Response updatePrivacy(UserPrivacySettingsDTO request, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> Response.ok(privacyService.updatePrivacy(userId, request)).build());
    }

    private Response withAuth(HttpHeaders headers, java.util.function.Function<UUID, Response> action) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return unauthorized();
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
            log.warn("Chat privacy auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Invalid or expired token")
                .build();
    }
}
