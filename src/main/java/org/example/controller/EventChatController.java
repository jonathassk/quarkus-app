package org.example.controller;

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
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.services.TokenService;
import org.example.application.services.event.EventChatService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Event Chat", description = "Chat de evento")
@Path("/api/v1/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class EventChatController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final EventChatService eventChatService;

    @GET
    @Path("/{id}/chat")
    @Transactional
    @Operation(summary = "Status do chat do evento")
    public Response getChatStatus(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventChatService.getStatus(id, userId)).build());
    }

    @POST
    @Path("/{id}/chat")
    @Transactional
    @Operation(summary = "Criar chat manual do evento")
    public Response createChat(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    ConversationInboxItemDTO conversation = eventChatService.createManual(id, userId);
                    return Response.status(Response.Status.CREATED).entity(conversation).build();
                });
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
            log.warn("Event chat auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(java.util.Map.of("code", "UNAUTHORIZED"))
                .build();
    }
}
