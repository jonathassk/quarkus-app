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
import org.example.application.dto.proposal.UpdateProposalStatusRequest;
import org.example.application.dto.proposal.UpdateTripPricingRequest;
import org.example.application.dto.proposal.UpsertProposalTiersRequest;
import org.example.application.services.TokenService;
import org.example.application.services.proposal.ProposalService;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Proposals", description = "Pricing, tiers e envio de proposta (editor do agente)")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripProposalController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final ProposalService proposalService;

    @PATCH
    @Path("/{tripId}/pricing")
    @Transactional
    @Operation(summary = "Atualizar custo base e recalcular preço final com markup")
    public Response updatePricing(
            @PathParam("tripId") UUID tripId,
            UpdateTripPricingRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(TripMapper.mapToTripResponseDTO(
                        proposalService.updatePricing(tripId, userId, request))).build());
    }

    @PUT
    @Path("/{tripId}/tiers")
    @Transactional
    @Operation(summary = "Substituir tiers da proposta")
    public Response upsertTiers(
            @PathParam("tripId") UUID tripId,
            UpsertProposalTiersRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(proposalService.upsertTiers(tripId, userId, request)).build());
    }

    @POST
    @Path("/{tripId}/proposal/send")
    @Transactional
    @Operation(summary = "Marcar proposta como SENT e garantir shareCode")
    public Response send(
            @PathParam("tripId") UUID tripId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(TripMapper.mapToTripResponseDTO(
                        proposalService.sendProposal(tripId, userId))).build());
    }

    @PATCH
    @Path("/{tripId}/proposal/status")
    @Transactional
    @Operation(summary = "Atualizar status do funil (Kanban)")
    public Response updateStatus(
            @PathParam("tripId") UUID tripId,
            UpdateProposalStatusRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(TripMapper.mapToTripResponseDTO(
                        proposalService.updateProposalStatus(
                                tripId, userId, request != null ? request.getProposalStatus() : null))).build());
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
            log.error("Trip proposal API error", e);
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
