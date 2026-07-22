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
import org.example.application.dto.event.*;
import org.example.application.services.TokenService;
import org.example.application.services.event.EventParticipantService;
import org.example.application.services.event.EventService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Events", description = "Eventos colaborativos")
@Path("/api/v1/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class EventController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final EventParticipantService participantService;

    @POST
    @Transactional
    @Operation(summary = "Criar evento")
    public Response create(CreateEventRequestDTO body, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.status(Response.Status.CREATED).entity(eventService.create(body, userId)).build());
    }

    @GET
    @Transactional
    @Operation(summary = "Listar meus eventos")
    public Response listMine(@Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventService.listMine(userId)).build());
    }

    @GET
    @Path("/public")
    @Transactional
    @Operation(summary = "Explorar eventos públicos")
    public Response listPublic(
            @QueryParam("city") String city,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("limit") Integer limit,
            @QueryParam("cursor") String cursor,
            @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId ->
                        Response.ok(
                                        eventService.listPublic(
                                                city, parseInstant(from), parseInstant(to), cursor, limit))
                                .build());
    }

    @GET
    @Path("/by-source")
    @Transactional
    @Operation(summary = "Buscar evento por atividade de trip")
    public Response getBySource(
            @QueryParam("tripId") UUID tripId,
            @QueryParam("activityId") String activityId,
            @Context HttpHeaders headers) {
        return withAuth(
                headers, userId -> Response.ok(eventService.getBySource(tripId, activityId, userId)).build());
    }

    @GET
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Obter evento")
    public Response get(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventService.get(id, userId)).build());
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Atualizar evento")
    public Response update(@PathParam("id") UUID id, UpdateEventRequestDTO body, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventService.update(id, body, userId)).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Cancelar evento")
    public Response cancel(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    eventService.cancel(id, userId);
                    return Response.noContent().build();
                });
    }

    @GET
    @Path("/{id}/participants")
    @Transactional
    @Operation(summary = "Listar participantes")
    public Response listParticipants(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(participantService.listParticipants(id, userId)).build());
    }

    @POST
    @Path("/{id}/invites")
    @Transactional
    @Operation(summary = "Convidar participantes")
    public Response invite(@PathParam("id") UUID id, InviteParticipantsRequestDTO body, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(participantService.invite(id, body, userId)).build());
    }

    @PATCH
    @Path("/{id}/rsvp")
    @Transactional
    @Operation(summary = "Responder convite (RSVP)")
    public Response rsvp(@PathParam("id") UUID id, RsvpRequestDTO body, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    participantService.rsvp(id, body, userId);
                    return Response.ok().build();
                });
    }

    @POST
    @Path("/{id}/join")
    @Transactional
    @Operation(summary = "Participar de evento público")
    public Response join(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    participantService.joinPublic(id, userId);
                    return Response.ok().build();
                });
    }

    @DELETE
    @Path("/{id}/participants/{userId}")
    @Transactional
    @Operation(summary = "Remover participante")
    public Response removeParticipant(
            @PathParam("id") UUID id, @PathParam("userId") UUID targetUserId, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    participantService.removeParticipant(id, targetUserId, userId);
                    return Response.noContent().build();
                });
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
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
            log.warn("Event auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(java.util.Map.of("code", "UNAUTHORIZED"))
                .build();
    }
}
