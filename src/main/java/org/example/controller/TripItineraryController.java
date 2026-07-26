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
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.services.TokenService;
import org.example.application.services.trip.TripItineraryService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Itinerary", description = "Atualização parcial de segmentos (refino IA)")
@Path("/api/v1/trips/{tripId}/segments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripItineraryController {

    private final TripItineraryService itineraryService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GET
    @Path("/{segmentId}")
    @Operation(summary = "Obter um segmento")
    public Response get(
            @PathParam("tripId") UUID tripId,
            @PathParam("segmentId") UUID segmentId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(itineraryService.getSegment(tripId, segmentId, userId)).build());
    }

    @PUT
    @Path("/{segmentId}")
    @Transactional
    @Operation(summary = "Substituir um segmento atomicamente (salva revisão para undo)")
    public Response replace(
            @PathParam("tripId") UUID tripId,
            @PathParam("segmentId") UUID segmentId,
            TripSegmentDTO body,
            @QueryParam("reason") @DefaultValue("REFINE") String reason,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(itineraryService.replaceSegment(tripId, segmentId, body, userId, reason))
                        .build());
    }

    @POST
    @Path("/{segmentId}/undo")
    @Transactional
    @Operation(summary = "Desfazer a última alteração do segmento")
    public Response undo(
            @PathParam("tripId") UUID tripId,
            @PathParam("segmentId") UUID segmentId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(itineraryService.undoSegment(tripId, segmentId, userId)).build());
    }

    private Response withUser(HttpHeaders headers, java.util.function.Function<UUID, Response> action) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                    .build();
        }
        try {
            return action.apply(userId.get());
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiErrorBody.builder().code("FORBIDDEN").message(e.getMessage()).build())
                    .build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                    .build();
        } catch (Exception e) {
            log.error("Itinerary API error", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiErrorBody.builder().code("INTERNAL_ERROR").message(e.getMessage()).build())
                    .build();
        }
    }

    private Optional<UUID> resolveAuthenticatedUserId(HttpHeaders headers) {
        String bearerLine = headers != null
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
}
