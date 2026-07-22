package org.example.controller;

import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.user.UserTravelPreferencesDTO;
import org.example.application.services.TokenService;
import org.example.application.services.user.TravelPreferencesService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

@Slf4j
@Tag(name = "Travel Preferences", description = "Preferências de viagem do usuário (moeda, dieta, logística)")
@Path("/api/v1/users/me/travel-preferences")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TravelPreferencesController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final TravelPreferencesService travelPreferencesService;

    @GET
    @Transactional
    @Operation(summary = "Obter preferências de viagem")
    public Response get(@Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(travelPreferencesService.getPreferences(userId)).build());
    }

    @PATCH
    @Transactional
    @Operation(summary = "Atualizar preferências de viagem")
    public Response update(UserTravelPreferencesDTO body, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> Response.ok(travelPreferencesService.updatePreferences(userId, body)).build());
    }

    private Response withAuth(HttpHeaders headers, java.util.function.Function<UUID, Response> action) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
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
            log.warn("Travel preferences auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
