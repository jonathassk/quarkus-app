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
import org.example.application.dto.trip.request.CreateTripShareLinkRequest;
import org.example.application.services.TokenService;
import org.example.application.services.trip.TripShareLinkService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Share Links", description = "Links públicos de leitura da viagem")
@Path("/api/v1/trips/{tripId}/share-links")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripShareLinkController {

    private final TripShareLinkService shareLinkService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @POST
    @Transactional
    @Operation(summary = "Criar ou rotacionar link público de leitura")
    public Response create(
            @PathParam("tripId") UUID tripId,
            CreateTripShareLinkRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(shareLinkService.createOrRotate(tripId, actorId.get(), body))
                    .build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (ForbiddenException e) {
            return forbidden(e);
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @GET
    @Operation(summary = "Listar links públicos da viagem")
    public Response list(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.ok(shareLinkService.list(tripId, actorId.get())).build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (ForbiddenException e) {
            return forbidden(e);
        }
    }

    @DELETE
    @Path("/{linkId}")
    @Transactional
    @Operation(summary = "Revogar link público")
    public Response revoke(
            @PathParam("tripId") UUID tripId,
            @PathParam("linkId") UUID linkId,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            shareLinkService.revoke(tripId, linkId, actorId.get());
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (ForbiddenException e) {
            return forbidden(e);
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

    private Response notFound(Exception e) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                .build();
    }

    private Response forbidden(Exception e) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ApiErrorBody.builder().code("FORBIDDEN").message(e.getMessage()).build())
                .build();
    }

    private Response badRequest(Exception e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                .build();
    }
}
