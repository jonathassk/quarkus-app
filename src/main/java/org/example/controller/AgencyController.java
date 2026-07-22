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
import org.example.application.dto.agency.*;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.dto.document.UploadDocumentRequest;
import org.example.application.services.TokenService;
import org.example.application.services.agency.AgencyService;
import org.example.application.services.proposal.ProposalService;
import org.example.domain.entity.AgencyMember;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.DocumentUploadSupport;
import org.example.utils.RequestAuthHeaders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Agency", description = "Branding white-label, equipe e analytics B2B")
@Path("/api/v1/agency")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AgencyController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AgencyService agencyService;
    private final ProposalService proposalService;
    private final ObjectStorageService objectStorageService;

    @GET
    @Path("/me")
    @Operation(summary = "Branding da agência do usuário logado")
    public Response getMe(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.getBrandingForUser(userId)).build());
    }

    @PATCH
    @Path("/me")
    @Transactional
    @Operation(summary = "Atualizar branding (somente OWNER)")
    public Response patchMe(UpdateAgencyBrandingRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.updateBranding(userId, request)).build());
    }

    @POST
    @Path("/me/logo-upload-request")
    @Transactional
    @Operation(summary = "Presign upload da logo no R2")
    public Response logoUploadRequest(UploadDocumentRequest req, @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            AgencyMember member = agencyService.requireOwner(userId);
            if (!objectStorageService.isConfigured()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("code", "STORAGE_NOT_CONFIGURED", "message", "Document storage is not configured"))
                        .build();
            }
            var resolved = DocumentUploadSupport.resolve(
                    req != null ? req.getFileName() : null,
                    req != null ? req.getContentType() : null);
            if (resolved.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("code", "INVALID_FILE",
                                "message", DocumentUploadSupport.unsupportedTypeMessage(
                                        req != null ? req.getContentType() : null,
                                        req != null ? req.getFileName() : null)))
                        .build();
            }
            var upload = resolved.get();
            String ext = "";
            int dot = upload.fileName().lastIndexOf('.');
            if (dot >= 0) {
                ext = upload.fileName().substring(dot);
            }
            String s3Key = "agencies/" + member.getAgency().id + "/logo-" + UUID.randomUUID() + ext;
            String uploadUrl = objectStorageService.presignPut(s3Key, upload.contentType());
            String publicUrl = objectStorageService.getPublicUrl(s3Key);
            return Response.status(Response.Status.CREATED).entity(Map.of(
                    "uploadUrl", uploadUrl,
                    "s3Key", s3Key,
                    "publicUrl", publicUrl,
                    "expiresInSeconds", objectStorageService.getUploadPresignSeconds()
            )).build();
        });
    }

    @POST
    @Path("/me/logo-confirm")
    @Transactional
    @Operation(summary = "Confirmar logo após upload R2")
    public Response logoConfirm(ConfirmAgencyLogoRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.confirmLogo(userId, request)).build());
    }

    @GET
    @Path("/team")
    @Operation(summary = "Listar membros da agência (OWNER)")
    public Response listTeam(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.listMembers(userId)).build());
    }

    @POST
    @Path("/team")
    @Transactional
    @Operation(summary = "Convidar membro (OWNER)")
    public Response inviteMember(InviteAgencyMemberRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyService.inviteMember(userId, request))
                        .build());
    }

    @DELETE
    @Path("/team/{userId}")
    @Transactional
    @Operation(summary = "Remover membro (OWNER)")
    public Response removeMember(@PathParam("userId") UUID memberUserId, @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            agencyService.removeMember(userId, memberUserId);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/audit")
    @Operation(summary = "Histórico de auditoria B2B")
    public Response audit(
            @QueryParam("tripId") UUID tripId,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.listAudit(userId, tripId, limit)).build());
    }

    @GET
    @Path("/pipeline")
    @Operation(summary = "Kanban de propostas da agência")
    public Response pipeline(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(proposalService.listPipeline(userId)).build());
    }

    @GET
    @Path("/analytics")
    @Operation(summary = "BI: conversão, destinos e faturamento previsto")
    public Response analytics(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(proposalService.analytics(userId)).build());
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
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agency API error", e);
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
            log.warn("Agency auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
