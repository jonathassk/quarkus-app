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
import org.example.application.dto.proposal.commercial.*;
import org.example.application.services.TokenService;
import org.example.application.services.proposal.CommercialProposalService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Tag(name = "Commercial Proposals", description = "Motor multiopção de propostas e precificação")
@Path("/api/v1/agency/proposals")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class CommercialProposalController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final CommercialProposalService commercialProposalService;

    @GET
    @Path("/{proposalId}")
    @Operation(summary = "Obter proposta comercial completa")
    public Response get(@PathParam("proposalId") UUID proposalId, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.get(userId, proposalId)).build());
    }

    @GET
    @Path("/by-trip/{tripId}")
    @Operation(summary = "Obter proposta comercial pelo Trip-opção")
    public Response getByTrip(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.getByTrip(userId, tripId)).build());
    }

    @POST
    @Path("/{proposalId}/options/{optionId}/duplicate")
    @Transactional
    @Operation(summary = "Duplicar opção (máx. 3)")
    public Response duplicateOption(
            @PathParam("proposalId") UUID proposalId,
            @PathParam("optionId") UUID optionId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.duplicateOption(userId, proposalId, optionId)).build());
    }

    @POST
    @Path("/{proposalId}/options/from-trip")
    @Transactional
    @Operation(summary = "Adicionar opção a partir de roteiro existente (CLONE ou LINK)")
    public Response addOptionFromTrip(
            @PathParam("proposalId") UUID proposalId,
            AddOptionFromTripRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.addOptionFromTrip(userId, proposalId, request)).build());
    }

    @PATCH
    @Path("/{proposalId}/options/{optionId}")
    @Transactional
    @Operation(summary = "Atualizar opção (copy + precificação rápida)")
    public Response updateOption(
            @PathParam("proposalId") UUID proposalId,
            @PathParam("optionId") UUID optionId,
            UpsertProposalOptionRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.updateOption(userId, proposalId, optionId, request)).build());
    }

    @DELETE
    @Path("/{proposalId}/options/{optionId}")
    @Transactional
    @Operation(summary = "Excluir opção")
    public Response deleteOption(
            @PathParam("proposalId") UUID proposalId,
            @PathParam("optionId") UUID optionId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.deleteOption(userId, proposalId, optionId)).build());
    }

    @POST
    @Path("/{proposalId}/items")
    @Transactional
    @Operation(summary = "Criar ou atualizar item comercial")
    public Response upsertItem(
            @PathParam("proposalId") UUID proposalId,
            UpsertProposalItemRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.upsertItem(userId, proposalId, request)).build());
    }

    @DELETE
    @Path("/{proposalId}/items/{itemId}")
    @Transactional
    @Operation(summary = "Excluir item")
    public Response deleteItem(
            @PathParam("proposalId") UUID proposalId,
            @PathParam("itemId") UUID itemId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.deleteItem(userId, proposalId, itemId)).build());
    }

    @PATCH
    @Path("/{proposalId}/pricing-mode")
    @Transactional
    @Operation(summary = "Alternar modo QUICK / DETAILED")
    public Response setPricingMode(
            @PathParam("proposalId") UUID proposalId,
            SetPricingModeRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.setPricingMode(userId, proposalId, request)).build());
    }

    @POST
    @Path("/{proposalId}/addons")
    @Transactional
    @Operation(summary = "Criar ou atualizar adicional")
    public Response upsertAddOn(
            @PathParam("proposalId") UUID proposalId,
            UpsertProposalAddOnRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.upsertAddOn(userId, proposalId, request)).build());
    }

    @POST
    @Path("/{proposalId}/adjustments")
    @Transactional
    @Operation(summary = "Aplicar desconto/ajuste com motivo")
    public Response createAdjustment(
            @PathParam("proposalId") UUID proposalId,
            CreateAdjustmentRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.createAdjustment(userId, proposalId, request)).build());
    }

    @PATCH
    @Path("/{proposalId}/settings")
    @Transactional
    @Operation(summary = "Visibilidade de preço, nota de recomendação, justificativa de margem")
    public Response updateSettings(
            @PathParam("proposalId") UUID proposalId,
            UpdateProposalSettingsRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.updateSettings(userId, proposalId, request)).build());
    }

    @POST
    @Path("/{proposalId}/items/{itemId}/convert-scope")
    @Transactional
    @Operation(summary = "Transformar item comum ↔ específico")
    public Response convertItemScope(
            @PathParam("proposalId") UUID proposalId,
            @PathParam("itemId") UUID itemId,
            ConvertItemScopeRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.convertItemScope(
                        userId,
                        proposalId,
                        itemId,
                        request != null ? request.getTargetScope() : null,
                        request != null ? request.getTargetOptionId() : null)).build());
    }

    @POST
    @Path("/{proposalId}/items/{itemId}/copy-to/{optionId}")
    @Transactional
    @Operation(summary = "Copiar item para outra opção")
    public Response copyItem(
            @PathParam("proposalId") UUID proposalId,
            @PathParam("itemId") UUID itemId,
            @PathParam("optionId") UUID optionId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.copyItemToOption(
                        userId, proposalId, itemId, optionId)).build());
    }

    @POST
    @Path("/{proposalId}/send")
    @Transactional
    @Operation(summary = "Enviar proposta (bloqueia versão)")
    public Response send(
            @PathParam("proposalId") UUID proposalId,
            SendCommercialProposalRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.send(userId, proposalId, request)).build());
    }

    @POST
    @Path("/{proposalId}/revise")
    @Transactional
    @Operation(summary = "Criar nova versão a partir da enviada")
    public Response revise(
            @PathParam("proposalId") UUID proposalId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(commercialProposalService.revise(userId, proposalId)).build());
    }

    private Response withUser(HttpHeaders headers, Function<UUID, Response> action) {
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
            log.error("Commercial proposal API error", e);
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
