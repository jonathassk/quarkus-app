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
import org.example.application.dto.template.SaveAsTemplateRequest;
import org.example.application.services.TokenService;
import org.example.application.services.payment.TripUnlockService;
import org.example.application.services.trip.TripTemplateService;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

/** Template ops under /trips — class path must match TripController prefix or JAX-RS returns 404. */
@Slf4j
@Tag(name = "Trip Templates", description = "Reuso de roteiros (pessoal e agência)")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripTemplateTripController {

    private final TripTemplateService templateService;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TripUnlockService tripUnlockService;

    @POST
    @Path("/{tripId}/save-as-template")
    @Transactional
    @Operation(summary = "Salvar viagem (ou bloco de segmento) como template")
    public Response saveAsTemplate(
            @PathParam("tripId") UUID tripId,
            SaveAsTemplateRequest body,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(templateService.saveFromTrip(tripId, userId, body))
                        .build());
    }

    @POST
    @Path("/from-template/{templateId}")
    @Transactional
    @Operation(summary = "Criar viagem a partir de template FULL_TRIP")
    public Response fromTemplate(
            @PathParam("templateId") UUID templateId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            var trip = templateService.createFromTemplate(templateId, userId);
            return Response.status(Response.Status.CREATED)
                    .entity(TripMapper.mapToTripResponseDTO(
                            trip, null, tripUnlockService.listKinds(trip.id)))
                    .build();
        });
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
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                    .build();
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
            log.error("Template trip API error", e);
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
