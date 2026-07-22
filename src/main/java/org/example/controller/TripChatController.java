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
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.services.TokenService;
import org.example.application.services.chat.TripChatService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;

@Slf4j
@Tag(name = "Trip Chat", description = "Chat contextual de viagem")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripChatController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TripChatService tripChatService;

    @GET
    @Path("/{tripId}/chat")
    @Transactional
    @Operation(summary = "Status do chat da viagem")
    public Response getTripChatStatus(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        return withAuth(
                headers, userId -> Response.ok(tripChatService.getStatus(tripId, userId)).build());
    }

    @POST
    @Path("/{tripId}/chat")
    @Transactional
    @Operation(summary = "Criar chat manual da viagem (solo trip, owner)")
    public Response createTripChat(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    ConversationInboxItemDTO conversation = tripChatService.createManual(tripId, userId);
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
            log.warn("Trip chat auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Invalid or expired token")
                .build();
    }
}
