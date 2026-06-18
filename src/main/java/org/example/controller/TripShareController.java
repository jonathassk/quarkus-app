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
import org.example.application.services.B2bAuditService;
import org.example.application.services.TokenService;
import org.example.application.services.TripCollaborationService;
import org.example.domain.entity.Trip;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Tag(name = "Trip Collaboration", description = "Compartilhamento de roteiros de viagem e gerenciamento de membros colaboradores")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripShareController {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TripCollaborationService tripCollaborationService;
    private final B2bAuditService auditService;

    @POST
    @Path("/{tripId}/share")
    @Transactional
    @Operation(
        summary = "Compartilhar viagem com colaboradores",
        description = "Adiciona novos usuários colaboradores à viagem com o nível de permissão especificado (READ, EDIT, MANAGE)."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Viagem compartilhada com sucesso"),
        @APIResponse(responseCode = "400", description = "Parâmetros inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido ou permissão insuficiente"),
        @APIResponse(responseCode = "404", description = "Viagem ou usuário não encontrados")
    })
    public Response shareTrip(
            @PathParam("tripId") Long tripId,
            @RequestBody(description = "Lista de usuários e nível de permissão", required = true) ShareTripRequestDTO request,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            Trip trip = tripCollaborationService.shareTrip(tripId, actorId.get(), request);

            int count = request != null && request.getUsers() != null ? request.getUsers().size() : 0;
            auditService.record(
                    trip, actorId.get(),
                    B2bTripLogAction.MEMBER_ADDED,
                    "MEMBER", null,
                    count + " membro(s) adicionado(s) à viagem");

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
    @Operation(
        summary = "Remover colaborador",
        description = "Remove o vínculo de um colaborador específico desta viagem."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Colaborador removido com sucesso"),
        @APIResponse(responseCode = "400", description = "Requisição inválida"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido ou permissão insuficiente"),
        @APIResponse(responseCode = "404", description = "Viagem ou colaborador não encontrados")
    })
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

            auditService.record(
                    trip, actorId.get(),
                    B2bTripLogAction.MEMBER_REMOVED,
                    "MEMBER", memberUserId,
                    "Membro userId=" + memberUserId + " removido da viagem");

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
    @Operation(
        summary = "Atualizar permissão de colaborador",
        description = "Modifica o nível de permissão de um colaborador já existente (READ, EDIT, MANAGE)."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Permissão do colaborador atualizada com sucesso"),
        @APIResponse(responseCode = "400", description = "Parâmetros inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido ou permissão insuficiente"),
        @APIResponse(responseCode = "404", description = "Viagem ou colaborador não encontrados")
    })
    public Response updateCollaboratorPermission(
            @PathParam("tripId") Long tripId,
            @PathParam("userId") Long memberUserId,
            @RequestBody(description = "Nova permissão", required = true) UpdateSharePermissionDTO body,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            Trip trip =
                    tripCollaborationService.updateMemberPermission(
                            tripId, actorId.get(), memberUserId, body);

            String newPerm = body != null ? body.getPermission() : "?";
            auditService.record(
                    trip, actorId.get(),
                    B2bTripLogAction.MEMBER_PERMISSION_CHANGED,
                    "MEMBER", memberUserId,
                    "Permissão de userId=" + memberUserId + " alterada para '" + newPerm + "'");

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
