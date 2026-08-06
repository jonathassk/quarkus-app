package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.dto.ops.*;
import org.example.application.services.TokenService;
import org.example.application.services.ops.OperationalWorkspaceService;
import org.example.domain.enums.OperationalDocumentKind;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Operations", description = "Workspace de operação e reservas pós-aprovação")
@Path("/api/v1/trips/{tripId}/operations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripOperationsController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final OperationalWorkspaceService workspaceService;

    @GET
    @Operation(summary = "Painel operacional da viagem (serviços, prazos, prontidão)")
    public Response getWorkspace(
            @PathParam("tripId") UUID tripId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.getWorkspace(tripId, userId)).build());
    }

    @POST
    @Path("/materialize")
    @Transactional
    @Operation(summary = "Forçar materialização dos serviços a partir da proposta aprovada")
    public Response materialize(
            @PathParam("tripId") UUID tripId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            workspaceService.getWorkspace(tripId, userId); // access check + lazy materialize
            workspaceService.materializeForTripId(tripId);
            return Response.ok(workspaceService.getWorkspace(tripId, userId)).build();
        });
    }

    @PATCH
    @Path("/services/{serviceId}/status")
    @Transactional
    @Operation(summary = "Atualizar status / próxima ação do serviço")
    public Response updateStatus(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            UpdateOperationalServiceStatusRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.updateStatus(tripId, serviceId, userId, request)).build());
    }

    @POST
    @Path("/services/{serviceId}/confirm")
    @Transactional
    @Operation(summary = "Registrar confirmação (localizador, valor, voucher)")
    public Response confirm(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            ConfirmOperationalServiceRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.confirm(tripId, serviceId, userId, request)).build());
    }

    @POST
    @Path("/services/{serviceId}/cancel")
    @Transactional
    @Operation(summary = "Registrar cancelamento do serviço")
    public Response cancel(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            CancelOperationalServiceRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.cancel(tripId, serviceId, userId, request)).build());
    }

    @PATCH
    @Path("/services/{serviceId}/publish")
    @Transactional
    @Operation(summary = "Publicar ou ocultar serviço confirmado para o viajante")
    public Response publish(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            Map<String, Boolean> body,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            boolean published = body != null && Boolean.TRUE.equals(body.get("published"));
            return Response.ok(workspaceService.setPublished(tripId, serviceId, userId, published)).build();
        });
    }

    @POST
    @Path("/services/{serviceId}/deadlines")
    @Transactional
    @Operation(summary = "Adicionar prazo operacional ao serviço")
    public Response addDeadline(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            AddOperationalDeadlineRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.addDeadline(tripId, serviceId, userId, request)).build());
    }

    @POST
    @Path("/deadlines/{deadlineId}/complete")
    @Transactional
    @Operation(summary = "Marcar prazo operacional como concluído")
    public Response completeDeadline(
            @PathParam("tripId") UUID tripId,
            @PathParam("deadlineId") UUID deadlineId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.completeDeadline(tripId, deadlineId, userId)).build());
    }

    @PUT
    @Path("/services/{serviceId}/passengers")
    @Transactional
    @Operation(summary = "Vincular passageiros ao serviço operacional")
    public Response linkPassengers(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            LinkPassengersRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.linkPassengers(tripId, serviceId, userId, request)).build());
    }

    @POST
    @Path("/services/{serviceId}/changes")
    @Transactional
    @Operation(summary = "Abrir solicitação de alteração do serviço")
    public Response createChangeRequest(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            CreateServiceChangeRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.createChangeRequest(tripId, serviceId, userId, request)).build());
    }

    @PATCH
    @Path("/changes/{changeId}")
    @Transactional
    @Operation(summary = "Atualizar status/nota de solicitação de alteração")
    public Response updateChangeRequest(
            @PathParam("tripId") UUID tripId,
            @PathParam("changeId") UUID changeId,
            UpdateServiceChangeRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.updateChangeRequest(tripId, changeId, userId, request)).build());
    }

    @PATCH
    @Path("/documents/{documentId}/status")
    @Transactional
    @Operation(summary = "Atualizar status operacional do documento")
    public Response updateDocumentStatus(
            @PathParam("tripId") UUID tripId,
            @PathParam("documentId") UUID documentId,
            UpdateOperationalDocumentStatusRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.updateDocumentStatus(tripId, documentId, userId, request)).build());
    }

    @POST
    @Path("/documents/{documentId}/link/{serviceId}")
    @Transactional
    @Operation(summary = "Vincular documento a um serviço operacional")
    public Response linkDocumentToService(
            @PathParam("tripId") UUID tripId,
            @PathParam("documentId") UUID documentId,
            @PathParam("serviceId") UUID serviceId,
            @QueryParam("documentKind") OperationalDocumentKind documentKind,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.linkDocumentToService(
                        tripId, documentId, serviceId, userId, documentKind)).build());
    }

    @PATCH
    @Path("/services/{serviceId}/supplier")
    @Transactional
    @Operation(summary = "Atribuir fornecedor ao serviço")
    public Response assignSupplier(
            @PathParam("tripId") UUID tripId,
            @PathParam("serviceId") UUID serviceId,
            AssignSupplierRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.assignSupplier(tripId, serviceId, userId, request)).build());
    }

    @GET
    @Path("/published")
    @Operation(summary = "Itinerário publicado (viajante) — serviços confirmados e docs CLIENT")
    public Response getPublished(
            @PathParam("tripId") UUID tripId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(workspaceService.getPublishedItinerary(tripId, userId)).build());
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
            log.error("Trip operations API error", e);
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
            UUID id = UUID.fromString(tokenService.validateToken(token));
            if (userRepository.findById(id) == null) {
                return Optional.empty();
            }
            return Optional.of(id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
