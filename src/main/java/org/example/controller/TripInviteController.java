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
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.dto.trip.request.CreateTripInviteRequest;
import org.example.application.services.TokenService;
import org.example.application.services.trip.TripInviteService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Invites", description = "Convites por e-mail para colaboradores sem conta")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripInviteController {

    private final TripInviteService inviteService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @POST
    @Path("/{tripId}/invites")
    @Transactional
    @Operation(summary = "Criar convite por e-mail e disparar e-mail")
    public Response create(
            @PathParam("tripId") UUID tripId,
            CreateTripInviteRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(inviteService.create(tripId, actorId.get(), body))
                    .build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        } catch (BadRequestException e) {
            return error(Response.Status.BAD_REQUEST, "BAD_REQUEST", e);
        }
    }

    @GET
    @Path("/{tripId}/invites")
    @Operation(summary = "Listar convites da viagem")
    public Response list(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.ok(inviteService.list(tripId, actorId.get())).build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        }
    }

    @DELETE
    @Path("/{tripId}/invites/{inviteId}")
    @Transactional
    @Operation(summary = "Revogar convite pendente")
    public Response revoke(
            @PathParam("tripId") UUID tripId,
            @PathParam("inviteId") UUID inviteId,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            inviteService.revoke(tripId, inviteId, actorId.get());
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        }
    }

    @POST
    @Path("/{tripId}/invites/{inviteId}/resend")
    @Transactional
    @Operation(summary = "Reenviar convite (novo token e validade)")
    public Response resend(
            @PathParam("tripId") UUID tripId,
            @PathParam("inviteId") UUID inviteId,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.ok(inviteService.resend(tripId, inviteId, actorId.get())).build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        } catch (BadRequestException e) {
            return error(Response.Status.BAD_REQUEST, "BAD_REQUEST", e);
        }
    }

    @POST
    @Path("/invites/{token}/accept")
    @Transactional
    @Operation(summary = "Aceitar convite (após login/cadastro)")
    public Response accept(@PathParam("token") String token, @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.ok(inviteService.accept(token, actorId.get())).build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (BadRequestException e) {
            return error(Response.Status.BAD_REQUEST, "BAD_REQUEST", e);
        }
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
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                .build();
    }

    private Response error(Response.Status status, String code, Exception e) {
        return Response.status(status)
                .entity(ApiErrorBody.builder().code(code).message(e.getMessage()).build())
                .build();
    }
}
