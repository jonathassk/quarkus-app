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
import org.example.application.dto.entitlement.ConsumeAiGenerationRequest;
import org.example.application.dto.entitlement.ConsumeAiGenerationResponse;
import org.example.application.dto.entitlement.EntitlementsDTO;
import org.example.application.exception.EntitlementExceededException;
import org.example.application.services.TokenService;
import org.example.application.services.entitlement.EntitlementService;
import org.example.domain.enums.AiGenerationKind;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Me / Entitlements", description = "Limites do plano do usuário autenticado")
@Path("/api/v1/me")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class MeEntitlementsController {

    private final EntitlementService entitlementService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GET
    @Path("/entitlements")
    @Operation(summary = "Obter limites e uso do plano atual")
    public Response getEntitlements(@Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUserId(headers);
        if (userId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                    .build();
        }
        try {
            EntitlementsDTO dto = entitlementService.getEntitlements(userId.get());
            return Response.ok(dto).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        }
    }

    @POST
    @Path("/ai-generations")
    @Transactional
    @Operation(summary = "Autorizar e registrar uma geração de IA (consome crédito do mês)")
    public Response consumeAiGeneration(
            ConsumeAiGenerationRequest request,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUserId(headers);
        if (userId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                    .build();
        }
        try {
            AiGenerationKind kind = request != null && request.getKind() != null
                    ? request.getKind()
                    : AiGenerationKind.PLAN;
            UUID tripId = request != null ? request.getTripId() : null;
            ConsumeAiGenerationResponse body =
                    entitlementService.consumeAiGeneration(userId.get(), tripId, kind);
            return Response.ok(body).build();
        } catch (EntitlementExceededException e) {
            throw e; // ExceptionMapper → 402
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        }
    }

    private Optional<UUID> resolveUserId(HttpHeaders headers) {
        String bearerLine = RequestAuthHeaders.resolveBearerHeaderLine(
                headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null,
                headers != null ? headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION) : null);
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
            log.warn("Me entitlements auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
