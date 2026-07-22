package org.example.controller;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.UserInlcudeRequestDTO;
import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.application.services.B2bAuditService;
import org.example.application.services.TokenService;
import org.example.application.services.TripCollaborationService;
import org.example.domain.entity.Agency;
import org.example.domain.entity.AgencyMember;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.UserPermissionLevel;
import org.example.domain.enums.UserType;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.RequestAuthHeaders;
import org.example.utils.TripDataValidator;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Trips", description = "Gerenciamento do ciclo de vida de viagens (criação, edição, exclusão e listagem)")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripController {

    private final CreateTripUseCase createTripUseCase;
    private final UpdateTripUseCase updateTripUseCase;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TokenService tokenService;
    private final TripCollaborationService tripCollaborationService;
    private final AgencyMemberRepository agencyMemberRepository;
    private final B2bAuditService auditService;

    private static final String UNAUTHORIZED_MSG = "Invalid or expired token";
    private static final String AUTH_HEADER_MSG = "Missing or invalid Authorization header";
    private static final String FORBIDDEN_TRIP_MSG = "You do not have access to this trip";

    private Optional<UUID> resolveAuthenticatedUserId(HttpHeaders headers) {
        String bearerLine =
                RequestAuthHeaders.resolveBearerHeaderLine(
                        headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null,
                        headers != null
                                ? headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION)
                                : null);
        if (bearerLine == null) {
            return Optional.empty();
        }
        try {
            String token = bearerLine.substring("Bearer ".length()).trim();
            UUID userId = UUID.fromString(tokenService.validateToken(token));
            if (userRepository.findById(userId) == null) {
                log.warn("Auth failed: user not found for userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Auth failed: invalid token ({})", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorizedResponse() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorBody("INVALID_TOKEN", UNAUTHORIZED_MSG))
                .build();
    }

    private Response missingAuthHeaderResponse() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorBody("MISSING_AUTH_HEADER", AUTH_HEADER_MSG))
                .build();
    }

    private static java.util.Map<String, String> errorBody(String code, String message) {
        return java.util.Map.of("code", code, "message", message != null ? message : "");
    }

    /**
     * Verifica se o usuário tem acesso à viagem.
     *
     * <p><strong>Lógica de bypass B2B:</strong> Se a viagem pertence a uma agência
     * ({@code trip.agency != null}), a verificação de assinatura B2C é ignorada.
     * Em vez disso:
     * <ul>
     *   <li>Usuário GUEST: acesso liberado se seu e-mail estiver vinculado à viagem.</li>
     *   <li>Membro da agência (AGENCY_OWNER/CONSULTANT): acesso liberado por pertencimento.</li>
     * </ul>
     * Viagens B2C ({@code trip.agency == null}) seguem o fluxo normal.
     */
    private Response ensureTripMember(UUID tripId, HttpHeaders headers) {
        String bearerLine =
                headers != null
                        ? RequestAuthHeaders.resolveBearerHeaderLine(
                                headers.getHeaderString(HttpHeaders.AUTHORIZATION),
                                headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION))
                        : null;
        if (bearerLine == null) {
            log.warn("Trip access denied: tripId={}, reason=missing_auth_header", tripId);
            return missingAuthHeaderResponse();
        }
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("Trip access denied: tripId={}, reason=invalid_token", tripId);
            return unauthorizedResponse();
        }
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            log.warn("Trip access denied: tripId={}, reason=trip_not_found", tripId);
            return Response.status(Response.Status.NOT_FOUND).entity("Trip not found").build();
        }

        UUID userId = userIdOpt.get();

        // -------------------------------------------------------------------
        // Bypass B2B: viagem pertence a uma agência
        // -------------------------------------------------------------------
        if (trip.getAgency() != null) {
            Agency agency = trip.getAgency();

            User user = userRepository.findById(userId);
            if (user == null) {
                return unauthorizedResponse();
            }

            // Caso 1: GUEST — verifica se o e-mail está vinculado à viagem
            if (UserType.GUEST == user.getUserType()) {
                boolean guestLinked = trip.getUsers() != null && trip.getUsers().stream()
                        .anyMatch(tu -> tu.getUser() != null
                                && user.getEmail().equalsIgnoreCase(tu.getUser().getEmail()));
                if (guestLinked) {
                    log.debug("Agency B2B bypass granted: GUEST userId={} tripId={}", userId, tripId);
                    return null; // acesso liberado
                }
                log.warn("Trip access denied: GUEST userId={} not linked to tripId={}", userId, tripId);
                return Response.status(Response.Status.FORBIDDEN).entity(FORBIDDEN_TRIP_MSG).build();
            }

            // Caso 2: Membro da agência — AGENCY_OWNER vê todas; CONSULTANT vê as suas
            boolean isAgencyMember = agencyMemberRepository
                    .isMemberWithRole(agency.id, userId, AgencyRole.AGENCY_CONSULTANT);
            if (isAgencyMember) {
                boolean isOwner = agencyMemberRepository
                        .isMemberWithRole(agency.id, userId, AgencyRole.AGENCY_OWNER);
                if (isOwner) {
                    log.debug("Agency B2B bypass granted: AGENCY_OWNER userId={} tripId={}", userId, tripId);
                    return null; // dono vê tudo
                }
                // Consultor: verifica se está vinculado à viagem
                if (tripRepository.isUserLinkedToTrip(tripId, userId)) {
                    log.debug("Agency B2B bypass granted: AGENCY_CONSULTANT userId={} tripId={}", userId, tripId);
                    return null;
                }
                log.warn("Trip access denied: AGENCY_CONSULTANT userId={} not linked to tripId={}", userId, tripId);
                return Response.status(Response.Status.FORBIDDEN).entity(FORBIDDEN_TRIP_MSG).build();
            }

            log.warn("Trip access denied: userId={} not agency member for agencyId={} tripId={}", userId, agency.id, tripId);
            return Response.status(Response.Status.FORBIDDEN).entity(FORBIDDEN_TRIP_MSG).build();
        }

        // -------------------------------------------------------------------
        // Fluxo normal B2C
        // -------------------------------------------------------------------
        if (!tripRepository.isUserLinkedToTrip(tripId, userId)) {
            log.warn("Trip access denied: tripId={}, userId={}, reason=not_member", tripId, userId);
            return Response.status(Response.Status.FORBIDDEN).entity(FORBIDDEN_TRIP_MSG).build();
        }
        return null;
    }

    @GET
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
        summary = "Listar viagens do usuário autenticado",
        description = "Retorna todas as viagens vinculadas ou pertencentes ao usuário autenticado atual."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de viagens retornada com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido, expirado ou ausente")
    })
    public Response listTripsForCurrentUser(@Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            if (RequestAuthHeaders.resolveBearerHeaderLine(
                            headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null,
                            headers != null
                                    ? headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION)
                                    : null)
                    == null) {
                log.warn("List trips rejected: missing auth header");
                return missingAuthHeaderResponse();
            }
            log.warn("List trips rejected: invalid token");
            return unauthorizedResponse();
        }
        List<TripResponseDTO> trips = tripRepository.findAllByLinkedUserId(userIdOpt.get()).stream()
                .map(t -> TripMapper.mapToTripResponseDTO(t, tripCollaborationService))
                .collect(Collectors.toList());
        return Response.ok(trips).build();
    }

    @POST
    @Transactional
    @Path("/create-trip")
    @Operation(
        summary = "Criar nova viagem",
        description = "Cria uma nova viagem com os detalhes fornecidos. O usuário autenticado é definido como criador."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Viagem criada com sucesso — retorna o ID da nova viagem"),
        @APIResponse(responseCode = "400", description = "Dados da viagem inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido, expirado ou ausente"),
        @APIResponse(responseCode = "404", description = "Entidades associadas não encontradas")
    })
    public Response createTrip(
            @Valid @RequestBody(description = "Dados para criação da viagem", required = true) TripRequestDTO tripRequest,
            @Context HttpHeaders headers) {
        try {
            // Resolve the authenticated user from the Neon Auth JWT (JIT sync if needed).
            // Overrides any createdBy sent in the body.
            Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
            if (userIdOpt.isEmpty()) {
                log.warn("Create trip rejected: unauthorized (name={})", tripRequest.getName());
                return unauthorizedResponse();
            }

            tripRequest.setCreatedBy(userIdOpt.get());

            TripDataValidator.validateTripRequest(tripRequest);
            Trip result = createTripUseCase.createTrip(tripRequest);
            return Response.status(Response.Status.CREATED).entity(result.id).build();
        } catch (IllegalArgumentException e) {
            log.warn("Create trip rejected: name={}, reason={}", tripRequest.getName(), e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            log.warn("Create trip rejected: {}", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Create trip failed: name={}", tripRequest.getName(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{tripId}")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
        summary = "Obter viagem por ID",
        description = "Retorna os detalhes completos de uma viagem específica se o usuário autenticado for membro/colaborador. " +
                      "Viagens de agência (B2B) aceitam bypass para guests vinculados."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Detalhes da viagem retornados com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada")
    })
    public Response getTripById(@PathParam("tripId") UUID tripId,
                               @Context HttpHeaders headers) {
        Response denied = ensureTripMember(tripId, headers);
        if (denied != null) {
            return denied;
        }
        Trip trip = tripRepository.findById(tripId);
        TripResponseDTO tripResponse = TripMapper.mapToTripResponseDTO(trip, tripCollaborationService);
        return Response.ok(tripResponse).build();
    }

    @PUT
    @Transactional
    @Path("/{tripId}/update-trip")
    @Operation(
        summary = "Atualizar viagem completa",
        description = "Substitui todos os dados da viagem (roteiro, atividades, etc.) pelos novos valores fornecidos. " +
                      "Operação auditada para viagens B2B."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Viagem atualizada com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Sem permissão de edição para esta viagem"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response updateTrip(@PathParam("tripId") UUID tripId,
                              @Valid @RequestBody(description = "Novos dados da viagem", required = true) TripRequestDTO tripRequest,
                              @Context HttpHeaders headers) {
        Response denied = ensureTripEditor(tripId, headers);
        if (denied != null) {
            return denied;
        }
        try {
            TripDataValidator.validateTripRequest(tripRequest);
            Trip updatedTrip = updateTripUseCase.updateTrip(tripId, tripRequest);
            TripResponseDTO tripResponse =
                    TripMapper.mapToTripResponseDTO(updatedTrip, tripCollaborationService);

            resolveAuthenticatedUserId(headers).ifPresent(uid ->
                    auditService.record(updatedTrip, uid,
                            B2bTripLogAction.TRIP_UPDATED,
                            "Viagem atualizada via PUT"));

            return Response.ok(tripResponse).build();
        } catch (NotFoundException e) {
            log.warn("Update trip rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            log.warn("Update trip rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update trip failed: tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while updating the trip: " + e.getMessage())
                    .build();
        }
    }

    @PATCH
    @Transactional
    @Path("/{tripId}/update-name-description")
    @Operation(
        summary = "Atualizar nome e descrição da viagem",
        description = "Atualiza parcialmente apenas o nome e a descrição de uma viagem específica. " +
                      "Operação auditada com snapshot de alteração para viagens B2B."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Nome e descrição atualizados com sucesso"),
        @APIResponse(responseCode = "400", description = "Campos obrigatórios vazios ou inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Sem permissão de edição para esta viagem"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response updateTripNameAndDescription(@PathParam("tripId") UUID tripId,
                                                   @Valid @RequestBody(description = "Novo nome e descrição", required = true) NameDescriptionTravelRequestDto request,
                                                   @Context HttpHeaders headers) {
        Response denied = ensureTripEditor(tripId, headers);
        if (denied != null) {
            return denied;
        }
        try {
            if (request.getName() == null || request.getDescription() == null
                    || request.getName().isBlank() || request.getDescription().isBlank()) {
                log.warn("Update trip name/description rejected: tripId={}, reason=empty_fields", tripId);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Name and description cannot be null or empty")
                        .build();
            }

            // Captura o nome anterior para o snapshot de auditoria
            Trip before = tripRepository.findById(tripId);
            String previousName = before != null ? before.getName() : null;

            Trip updatedTrip = updateTripUseCase.updateNameAndDescription(tripId, request);
            TripResponseDTO tripResponse =
                    TripMapper.mapToTripResponseDTO(updatedTrip, tripCollaborationService);

            resolveAuthenticatedUserId(headers).ifPresent(uid -> {
                String prevJson = previousName != null
                        ? "{\"name\": \"" + esc(previousName) + "\"}" : null;
                String newJson = "{\"name\": \"" + esc(request.getName()) + "\"}";
                auditService.record(
                        updatedTrip, uid,
                        B2bTripLogAction.TRIP_UPDATED,
                        "TRIP", tripId,
                        prevJson, newJson,
                        "Nome/descrição da viagem alterado para '" + request.getName() + "'",
                        null);
            });

            return Response.ok(tripResponse).build();
        } catch (NotFoundException e) {
            log.warn("Update trip name/description rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update trip name/description failed: tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while updating the trip: " + e.getMessage())
                    .build();
        }
    }

    @PATCH
    @Path("/{tripId}/update-users-trip")
    @Transactional
    @Operation(
        summary = "Atualizar colaboradores da viagem",
        description = "Adiciona, remove ou atualiza permissões de múltiplos colaboradores na viagem."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Colaboradores atualizados com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados da requisição inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Sem permissão de gerenciamento (manager/owner)"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response updateUsersTrip(@PathParam("tripId") UUID tripId,
                                   @Valid @RequestBody(description = "Lista de colaboradores e permissões", required = true) List<UserInlcudeRequestDTO> request,
                                   @Context HttpHeaders headers) {
        Response denied = ensureTripManager(tripId, headers);
        if (denied != null) {
            return denied;
        }
        try {
            request.forEach(userRequest -> {
                if (userRequest.getUserId() == null || userRequest.getPermissionLevel() == null) {
                    throw new IllegalArgumentException("User ID and permission level cannot be null");
                }
            });
            Trip updatedTrip = updateTripUseCase.updateUsersTrip(tripId, request);
            return Response.status(201).entity(updatedTrip).build();
        } catch (IllegalArgumentException e) {
            log.warn("Update trip users rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            log.warn("Update trip users rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update trip users failed: tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while updating trip users: " + e.getMessage())
                    .build();
        }
    }

    @DELETE
    @Path("/{tripId}")
    @Transactional
    @Operation(
        summary = "Excluir viagem",
        description = "Exclui permanentemente uma viagem específica se o usuário autenticado for o proprietário. " +
                      "Operação auditada para viagens B2B."
    )
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Viagem excluída com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Sem permissão de exclusão (apenas owner)"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response deleteTrip(@PathParam("tripId") UUID tripId,
                            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            if (RequestAuthHeaders.resolveBearerHeaderLine(
                            headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null,
                            headers != null
                                    ? headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION)
                                    : null)
                    == null) {
                log.warn("Delete trip rejected: tripId={}, reason=missing_auth_header", tripId);
                return missingAuthHeaderResponse();
            }
            log.warn("Delete trip rejected: tripId={}, reason=invalid_token", tripId);
            return unauthorizedResponse();
        }
        try {
            // Captura dados da viagem antes de deletar para o log de auditoria
            Trip tripBeforeDelete = tripRepository.findById(tripId);
            String tripName = tripBeforeDelete != null ? tripBeforeDelete.getName() : null;

            updateTripUseCase.deleteTrip(tripId, userIdOpt.get());

            if (tripBeforeDelete != null) {
                auditService.record(
                        tripBeforeDelete, userIdOpt.get(),
                        B2bTripLogAction.TRIP_DELETED,
                        "Viagem '" + (tripName != null ? tripName : tripId) + "' excluída");
            }

            return Response.noContent().build();
        } catch (NotFoundException e) {
            log.warn("Delete trip rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ForbiddenException e) {
            log.warn("Delete trip rejected: tripId={}, userId={}, reason={}", tripId, userIdOpt.get(), e.getMessage());
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Delete trip failed: tripId={}, userId={}", tripId, userIdOpt.get(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while deleting the trip: " + e.getMessage())
                    .build();
        }
    }

    private Response ensureTripEditor(UUID tripId, HttpHeaders headers) {
        Response base = ensureTripMember(tripId, headers);
        if (base != null) {
            return base;
        }
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        Trip trip = tripRepository.findById(tripId);
        if (userIdOpt.isEmpty() || trip == null) {
            return unauthorizedResponse();
        }
        UserPermissionLevel level = tripCollaborationService.resolvePermission(trip, userIdOpt.get());
        if (level == null || !level.canEdit()) {
            log.warn("Trip edit denied: tripId={}, userId={}", tripId, userIdOpt.get());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("You do not have permission to edit this trip")
                    .build();
        }
        return null;
    }

    private Response ensureTripManager(UUID tripId, HttpHeaders headers) {
        Response base = ensureTripMember(tripId, headers);
        if (base != null) {
            return base;
        }
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        Trip trip = tripRepository.findById(tripId);
        if (userIdOpt.isEmpty() || trip == null) {
            return unauthorizedResponse();
        }
        UserPermissionLevel level = tripCollaborationService.resolvePermission(trip, userIdOpt.get());
        if (level == null || !level.canManageUsers()) {
            log.warn("Trip share denied: tripId={}, userId={}", tripId, userIdOpt.get());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Only the trip owner can manage collaborators")
                    .build();
        }
        return null;
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }

    /** Escapa aspas duplas para embedding seguro em strings JSON inline. */
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
