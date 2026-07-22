package org.example.controller;

import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.trip.request.SendTripEmailRequestDTO;
import org.example.application.dto.trip.response.SendTripEmailResponseDTO;
import org.example.application.services.TokenService;
import org.example.application.services.trip.TripEmailService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

@Slf4j
@Tag(name = "Trip Email", description = "Envio de roteiro por e-mail")
@Path("/api/v1/trips/{tripId}/email")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripEmailController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TripEmailService tripEmailService;

    @POST
    @Transactional
    @Operation(summary = "Enviar resumo do roteiro por e-mail")
    public Response send(
            @PathParam("tripId") UUID tripId,
            SendTripEmailRequestDTO body,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }

        try {
            SendTripEmailResponseDTO result = tripEmailService.sendTripEmail(tripId, userId.get(), body);
            return Response.accepted(result).build();
        } catch (IllegalArgumentException e) {
            if ("TRIP_NOT_FOUND".equals(e.getMessage())) {
                return Response.status(Response.Status.NOT_FOUND).entity("Trip not found").build();
            }
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("You do not have access to this trip")
                    .build();
        } catch (Exception e) {
            log.error("Trip email failed tripId={}: {}", tripId, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to send email")
                    .build();
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
            UUID id = UUID.fromString(tokenService.validateToken(token));
            if (userRepository.findById(id) == null) {
                return Optional.empty();
            }
            return Optional.of(id);
        } catch (Exception e) {
            log.warn("Trip email auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
