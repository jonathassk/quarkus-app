package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.ShareTripRequestDTO;
import org.example.application.dto.trip.request.UpdateSharePermissionDTO;
import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.application.services.TokenService;
import org.example.application.services.TripCollaborationService;
import org.example.domain.entity.Trip;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;

@Slf4j
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripShareController {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TripCollaborationService tripCollaborationService;

    @POST
    @Path("/{tripId}/share")
    @Transactional
    public Response shareTrip(
            @PathParam("tripId") Long tripId,
            ShareTripRequestDTO request,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            Trip trip = tripCollaborationService.shareTrip(tripId, actorId.get(), request);
            return Response.ok(mapTrip(trip)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Share trip failed tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to share trip")
                    .build();
        }
    }

    @DELETE
    @Path("/{tripId}/share/{userId}")
    @Transactional
    public Response removeCollaborator(
            @PathParam("tripId") Long tripId,
            @PathParam("userId") Long memberUserId,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            Trip trip = tripCollaborationService.removeMember(tripId, actorId.get(), memberUserId);
            return Response.ok(mapTrip(trip)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Remove collaborator failed tripId={} userId={}", tripId, memberUserId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to remove collaborator")
                    .build();
        }
    }

    @PATCH
    @Path("/{tripId}/share/{userId}")
    @Transactional
    public Response updateCollaboratorPermission(
            @PathParam("tripId") Long tripId,
            @PathParam("userId") Long memberUserId,
            UpdateSharePermissionDTO body,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            Trip trip =
                    tripCollaborationService.updateMemberPermission(
                            tripId, actorId.get(), memberUserId, body);
            return Response.ok(mapTrip(trip)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update collaborator permission failed tripId={} userId={}", tripId, memberUserId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to update permission")
                    .build();
        }
    }

    private TripResponseDTO mapTrip(Trip trip) {
        Trip fresh = tripRepository.findById(trip.id);
        return TripMapper.mapToTripResponseDTO(fresh != null ? fresh : trip, tripCollaborationService);
    }

    private Optional<Long> resolveAuthenticatedUserId(HttpHeaders headers) {
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
            Long userId = Long.valueOf(tokenService.validateToken(token));
            if (userRepository.findById(userId) == null) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Share auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Invalid or expired token")
                .build();
    }
}
