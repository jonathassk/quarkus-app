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
import org.example.application.dto.passenger.CreateTripPassengerRequest;
import org.example.application.dto.passenger.RequestPassengerCorrectionRequest;
import org.example.application.dto.passenger.ResolvePassengerCorrectionRequest;
import org.example.application.dto.passenger.UpdateTripPassengerRequest;
import org.example.application.services.TokenService;
import org.example.application.services.passenger.PassengerAlertService;
import org.example.application.services.passenger.TripPassengerService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Passengers", description = "Lista e formulários de passageiros da viagem")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripPassengerController {

    private final TripPassengerService passengerService;
    private final PassengerAlertService alertService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GET
    @Path("/{tripId}/passengers")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(summary = "Listar passageiros da viagem")
    public Response list(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.list(tripId)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("List passengers failed tripId={}", tripId, e);
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers")
    @Transactional
    @Operation(summary = "Adicionar passageiro")
    public Response create(
            @PathParam("tripId") UUID tripId,
            CreateTripPassengerRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.status(Response.Status.CREATED)
                    .entity(passengerService.create(tripId, body, userId.get()))
                    .build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Create passenger failed tripId={}", tripId, e);
            return serverError();
        }
    }

    @PATCH
    @Path("/{tripId}/passengers/{passengerId}")
    @Transactional
    @Operation(summary = "Atualizar passageiro")
    public Response update(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            UpdateTripPassengerRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.update(tripId, passengerId, body, userId.get())).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Update passenger failed tripId={} passengerId={}", tripId, passengerId, e);
            return serverError();
        }
    }

    @DELETE
    @Path("/{tripId}/passengers/{passengerId}")
    @Transactional
    @Operation(summary = "Remover passageiro")
    public Response delete(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            passengerService.delete(tripId, passengerId, userId.get());
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("Delete passenger failed tripId={} passengerId={}", tripId, passengerId, e);
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/seed")
    @Transactional
    @Operation(summary = "Gerar slots de passageiros a partir da oportunidade")
    public Response seed(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.seedFromOpportunity(tripId, userId.get())).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("Seed passengers failed tripId={}", tripId, e);
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/{passengerId}/invite")
    @Transactional
    @Operation(summary = "Enviar link do formulário por e-mail")
    public Response invite(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            var result = passengerService.invite(tripId, passengerId, userId.get());
            try {
                alertService.syncAlertsForTrip(tripId);
            } catch (Exception ignored) {
                // non-blocking
            }
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Invite passenger failed tripId={} passengerId={}", tripId, passengerId, e);
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/{passengerId}/mark-reviewed")
    @Transactional
    @Operation(summary = "Marcar formulário/documento como conferido")
    public Response markReviewed(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.markReviewed(tripId, passengerId, userId.get())).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("Mark reviewed failed tripId={} passengerId={}", tripId, passengerId, e);
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/{passengerId}/copy-from-client")
    @Transactional
    @Operation(summary = "Copiar dados do cliente CRM para o passageiro")
    public Response copyFromClient(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.copyFromClient(tripId, passengerId, userId.get())).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Copy from client failed tripId={} passengerId={}", tripId, passengerId, e);
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/{passengerId}/request-correction")
    @Transactional
    @Operation(summary = "Solicitar correção de um campo do formulário")
    public Response requestCorrection(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            RequestPassengerCorrectionRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.status(Response.Status.CREATED)
                    .entity(passengerService.requestCorrection(tripId, passengerId, body, userId.get()))
                    .build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Request correction failed", e);
            return serverError();
        }
    }

    @GET
    @Path("/{tripId}/passengers/{passengerId}/corrections")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(summary = "Listar correções do passageiro")
    public Response listCorrections(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.listCorrections(tripId, passengerId)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/{passengerId}/corrections/{correctionId}/resolve")
    @Transactional
    @Operation(summary = "Resolver correção (agente)")
    public Response resolveCorrection(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @PathParam("correctionId") UUID correctionId,
            ResolvePassengerCorrectionRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.resolveCorrectionByAgent(
                            tripId, passengerId, correctionId, body, userId.get()))
                    .build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/{passengerId}/apply-reusable-profile")
    @Transactional
    @Operation(summary = "Aplicar perfil do viajante com consentimento na agência")
    public Response applyReusableProfile(
            @PathParam("tripId") UUID tripId,
            @PathParam("passengerId") UUID passengerId,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            return Response.ok(passengerService.applyReusableProfile(tripId, passengerId, userId.get()))
                    .build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return serverError();
        }
    }

    @POST
    @Path("/{tripId}/passengers/sync-alerts")
    @Transactional
    @Operation(summary = "Gerar tarefas de alerta para pendências de passageiros")
    public Response syncAlerts(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUser(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            passengerService.requireTripAccess(tripId, userId.get());
            int n = alertService.syncAlertsForTrip(tripId);
            return Response.ok(java.util.Map.of("created", n)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (Exception e) {
            log.error("Sync passenger alerts failed tripId={}", tripId, e);
            return serverError();
        }
    }

    private Optional<UUID> resolveUser(HttpHeaders headers) {
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
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                .build();
    }

    private Response forbidden(String message) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ApiErrorBody.builder().code("FORBIDDEN").message(message).build())
                .build();
    }

    private Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiErrorBody.builder().code("NOT_FOUND").message(message).build())
                .build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(message).build())
                .build();
    }

    private Response serverError() {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiErrorBody.builder().code("INTERNAL_ERROR").message("Internal error").build())
                .build();
    }
}
