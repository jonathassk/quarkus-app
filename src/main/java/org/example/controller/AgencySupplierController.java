package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
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
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.dto.ops.UpsertAgencySupplierRequest;
import org.example.application.services.TokenService;
import org.example.application.services.agency.AgencySupplierService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Agency Suppliers", description = "Cadastro de fornecedores da agência")
@Path("/api/v1/agency/suppliers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AgencySupplierController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AgencySupplierService supplierService;

    @GET
    @Operation(summary = "Listar fornecedores da agência")
    public Response list(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(supplierService.listSuppliers(userId)).build());
    }

    @POST
    @Transactional
    @Operation(summary = "Criar fornecedor")
    public Response create(UpsertAgencySupplierRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(supplierService.upsertSupplier(userId, null, request))
                        .build());
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Atualizar fornecedor")
    public Response update(
            @PathParam("id") UUID id,
            UpsertAgencySupplierRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(supplierService.upsertSupplier(userId, id, request)).build());
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
            log.error("Agency suppliers API error", e);
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
