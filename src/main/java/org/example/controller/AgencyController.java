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
import org.example.application.services.agency.AgencyAgendaService;
import org.example.application.services.agency.AgencyClientService;
import org.example.application.services.agency.AgencyOnboardingService;
import org.example.application.services.agency.AgencyOpportunityService;
import org.example.application.services.agency.AgencyService;
import org.example.application.services.proposal.CommercialProposalService;
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
    private final AgencyClientService agencyClientService;
    private final AgencyOnboardingService agencyOnboardingService;
    private final AgencyOpportunityService agencyOpportunityService;
    private final AgencyAgendaService agencyAgendaService;
    private final ProposalService proposalService;
    private final CommercialProposalService commercialProposalService;
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

    @POST
    @Path("/me/agent-photo-upload-request")
    @Transactional
    @Operation(summary = "Presign upload da foto do agente no R2")
    public Response agentPhotoUploadRequest(UploadDocumentRequest req, @Context HttpHeaders headers) {
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
            String s3Key = "agencies/" + member.getAgency().id + "/agent-photo-" + UUID.randomUUID() + ext;
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
    @Path("/me/agent-photo-confirm")
    @Transactional
    @Operation(summary = "Confirmar foto do agente após upload R2")
    public Response agentPhotoConfirm(ConfirmAgencyLogoRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.confirmAgentPhoto(userId, request)).build());
    }

    @GET
    @Path("/onboarding")
    @Operation(summary = "Estado do onboarding B2B")
    public Response getOnboarding(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.getOnboarding(userId)).build());
    }

    @PATCH
    @Path("/onboarding")
    @Transactional
    @Operation(summary = "Atualizar passo / progresso do onboarding")
    public Response patchOnboarding(UpdateAgencyOnboardingRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.updateOnboarding(userId, request)).build());
    }

    @POST
    @Path("/demo")
    @Transactional
    @Operation(summary = "Gerar dados de demonstração do onboarding")
    public Response seedDemo(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyOnboardingService.seedDemo(userId))
                        .build());
    }

    @DELETE
    @Path("/demo")
    @Transactional
    @Operation(summary = "Apagar dados de demonstração do onboarding")
    public Response clearDemo(@Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            agencyOnboardingService.clearDemo(userId);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/team")
    @Operation(summary = "Listar membros e convites pendentes (OWNER)")
    public Response listTeam(@Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.listTeam(userId)).build());
    }

    @POST
    @Path("/team")
    @Transactional
    @Operation(summary = "Convidar membro — ACTIVE se já tem conta, PENDING caso contrário (OWNER)")
    public Response inviteMember(InviteAgencyMemberRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyService.inviteMember(userId, request))
                        .build());
    }

    @POST
    @Path("/team/invites/{token}/accept")
    @Transactional
    @Operation(summary = "Aceitar convite pendente da agência")
    public Response acceptInvite(@PathParam("token") String token, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyService.acceptInvite(userId, token)).build());
    }

    @DELETE
    @Path("/team/invites/{inviteId}")
    @Transactional
    @Operation(summary = "Revogar convite pendente (OWNER)")
    public Response revokeInvite(@PathParam("inviteId") UUID inviteId, @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            agencyService.revokeInvite(userId, inviteId);
            return Response.noContent().build();
        });
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
    @Path("/clients")
    @Operation(summary = "Listar clientes CRM da agência")
    public Response listClients(
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyClientService.list(userId, q, page, size)).build());
    }

    @POST
    @Path("/clients")
    @Transactional
    @Operation(summary = "Criar cliente CRM")
    public Response createClient(UpsertAgencyClientRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyClientService.create(userId, request))
                        .build());
    }

    @POST
    @Path("/clients/import")
    @Transactional
    @Operation(summary = "Importar clientes CRM em lote (CSV/JSON)")
    public Response importClients(ImportAgencyClientsRequest request, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyClientService.importClients(userId, request)).build());
    }

    @GET
    @Path("/clients/{clientId}")
    @Operation(summary = "Ficha 360 do cliente")
    public Response getClient(@PathParam("clientId") UUID clientId, @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyClientService.get(userId, clientId)).build());
    }

    @PATCH
    @Path("/clients/{clientId}")
    @Transactional
    @Operation(summary = "Atualizar cliente CRM")
    public Response updateClient(
            @PathParam("clientId") UUID clientId,
            UpsertAgencyClientRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyClientService.update(userId, clientId, request)).build());
    }

    @DELETE
    @Path("/clients/{clientId}")
    @Transactional
    @Operation(summary = "Remover cliente CRM")
    public Response deleteClient(@PathParam("clientId") UUID clientId, @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            agencyClientService.delete(userId, clientId);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/opportunities")
    @Operation(summary = "Listar solicitações/oportunidades da agência")
    public Response listOpportunities(
            @QueryParam("stage") String stage,
            @QueryParam("consultantId") UUID consultantId,
            @QueryParam("clientId") UUID clientId,
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("100") int size,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.list(
                        userId, stage, consultantId, clientId, q, page, size)).build());
    }

    @GET
    @Path("/opportunities/duplicates")
    @Operation(summary = "Verificar contatos duplicados na agência")
    public Response checkOpportunityDuplicates(
            @QueryParam("email") String email,
            @QueryParam("phone") String phone,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.checkDuplicateContacts(userId, email, phone)).build());
    }

    @POST
    @Path("/opportunities")
    @Transactional
    @Operation(summary = "Criar solicitação/oportunidade (cadastro rápido ou completo)")
    public Response createOpportunity(
            UpsertAgencyOpportunityRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyOpportunityService.create(userId, request))
                        .build());
    }

    @GET
    @Path("/opportunities/{opportunityId}")
    @Operation(summary = "Detalhe da solicitação/oportunidade")
    public Response getOpportunity(
            @PathParam("opportunityId") UUID opportunityId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.get(userId, opportunityId)).build());
    }

    @GET
    @Path("/opportunities/{opportunityId}/activities")
    @Operation(summary = "Listar atividades da oportunidade")
    public Response listOpportunityActivities(
            @PathParam("opportunityId") UUID opportunityId,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.listActivities(userId, opportunityId, limit)).build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/activities")
    @Transactional
    @Operation(summary = "Registrar atividade na oportunidade")
    public Response addOpportunityActivity(
            @PathParam("opportunityId") UUID opportunityId,
            AddOpportunityActivityRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyOpportunityService.addActivity(userId, opportunityId, request))
                        .build());
    }

    @PATCH
    @Path("/opportunities/{opportunityId}")
    @Transactional
    @Operation(summary = "Atualizar solicitação/oportunidade")
    public Response updateOpportunity(
            @PathParam("opportunityId") UUID opportunityId,
            UpsertAgencyOpportunityRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.update(userId, opportunityId, request)).build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/lost")
    @Transactional
    @Operation(summary = "Marcar oportunidade como perdida (motivo obrigatório)")
    public Response markOpportunityLost(
            @PathParam("opportunityId") UUID opportunityId,
            MarkOpportunityLostRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.markLost(
                        userId,
                        opportunityId,
                        request)).build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/won")
    @Transactional
    @Operation(summary = "Marcar oportunidade como ganha (prospect → cliente)")
    public Response markOpportunityWon(
            @PathParam("opportunityId") UUID opportunityId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.markWon(userId, opportunityId)).build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/convert")
    @Transactional
    @Operation(summary = "Converter solicitação em proposta (Trip) sem redigitar")
    public Response convertOpportunity(
            @PathParam("opportunityId") UUID opportunityId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.convertToProposal(userId, opportunityId)).build());
    }

    @GET
    @Path("/agenda")
    @Operation(summary = "Agenda operacional do agente (atrasadas, hoje, próximas, aguardando, sem ação)")
    public Response getAgenda(
            @QueryParam("assigneeId") UUID assigneeId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyAgendaService.getAgenda(userId, assigneeId)).build());
    }

    @GET
    @Path("/opportunities/{opportunityId}/tasks")
    @Operation(summary = "Listar tarefas da solicitação")
    public Response listOpportunityTasks(
            @PathParam("opportunityId") UUID opportunityId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.listTasks(userId, opportunityId)).build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/tasks")
    @Transactional
    @Operation(summary = "Criar tarefa / próxima ação na solicitação")
    public Response createOpportunityTask(
            @PathParam("opportunityId") UUID opportunityId,
            UpsertOpportunityTaskRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyAgendaService.createTask(userId, opportunityId, request))
                        .build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/next-action")
    @Transactional
    @Operation(summary = "Definir ou substituir a próxima ação da oportunidade")
    public Response setNextAction(
            @PathParam("opportunityId") UUID opportunityId,
            SetNextActionRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.status(Response.Status.CREATED)
                        .entity(agencyAgendaService.setNextAction(userId, opportunityId, request))
                        .build());
    }

    @PATCH
    @Path("/opportunities/{opportunityId}/tasks/{taskId}")
    @Transactional
    @Operation(summary = "Atualizar tarefa da solicitação")
    public Response updateOpportunityTask(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("taskId") UUID taskId,
            UpsertOpportunityTaskRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyAgendaService.updateTask(userId, opportunityId, taskId, request))
                        .build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/tasks/{taskId}/complete")
    @Transactional
    @Operation(summary = "Concluir tarefa e opcionalmente definir próximo passo")
    public Response completeOpportunityTask(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("taskId") UUID taskId,
            CompleteOpportunityTaskRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyAgendaService.completeTask(userId, opportunityId, taskId, request))
                        .build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/tasks/{taskId}/defer")
    @Transactional
    @Operation(summary = "Adiar data da tarefa")
    public Response deferOpportunityTask(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("taskId") UUID taskId,
            DeferOpportunityTaskRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyAgendaService.deferTask(userId, opportunityId, taskId, request))
                        .build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/tasks/{taskId}/waiting")
    @Transactional
    @Operation(summary = "Marcar tarefa como aguardando terceiro (com data de revisão)")
    public Response waitingOpportunityTask(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("taskId") UUID taskId,
            WaitingOpportunityTaskRequest request,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyAgendaService.markWaiting(userId, opportunityId, taskId, request))
                        .build());
    }

    @DELETE
    @Path("/opportunities/{opportunityId}/tasks/{taskId}")
    @Transactional
    @Operation(summary = "Excluir tarefa da solicitação")
    public Response deleteOpportunityTask(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("taskId") UUID taskId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            agencyAgendaService.deleteTask(userId, opportunityId, taskId);
            return Response.noContent().build();
        });
    }

    @GET
    @Path("/opportunities/{opportunityId}/files")
    @Operation(summary = "Listar arquivos comerciais da solicitação")
    public Response listOpportunityFiles(
            @PathParam("opportunityId") UUID opportunityId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.listFiles(userId, opportunityId)).build());
    }

    @POST
    @Path("/opportunities/{opportunityId}/files/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @Operation(summary = "Anexar arquivo comercial (cotação/PDF/imagem)")
    public Response uploadOpportunityFile(
            @PathParam("opportunityId") UUID opportunityId,
            org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput multipart,
            @QueryParam("kind") String kind,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            var map = multipart.getFormDataMap();
            var fileParts = map.get("file");
            if (fileParts == null || fileParts.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("code", "VALIDATION_ERROR", "message", "file is required"))
                        .build();
            }
            var filePart = fileParts.getFirst();
            String rawName = null;
            try {
                var cd = filePart.getHeaders().getFirst("Content-Disposition");
                if (cd != null) {
                    for (String token : cd.split(";")) {
                        token = token.trim();
                        if (token.startsWith("filename=")) {
                            rawName = token.substring("filename=".length()).replace("\"", "");
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
            String browserCt = filePart.getMediaType() != null ? filePart.getMediaType().toString() : null;
            byte[] bytes;
            try {
                bytes = filePart.getBody(byte[].class, null);
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("code", "VALIDATION_ERROR", "message", "Could not read uploaded file"))
                        .build();
            }
            return Response.status(Response.Status.CREATED)
                    .entity(agencyOpportunityService.uploadFile(
                            userId, opportunityId, rawName, browserCt, bytes, kind))
                    .build();
        });
    }

    @GET
    @Path("/opportunities/{opportunityId}/files/{fileId}/view")
    @Operation(summary = "URL temporária de visualização do arquivo")
    public Response viewOpportunityFile(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("fileId") UUID fileId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId ->
                Response.ok(agencyOpportunityService.getFileView(userId, opportunityId, fileId)).build());
    }

    @DELETE
    @Path("/opportunities/{opportunityId}/files/{fileId}")
    @Transactional
    @Operation(summary = "Remover arquivo comercial")
    public Response deleteOpportunityFile(
            @PathParam("opportunityId") UUID opportunityId,
            @PathParam("fileId") UUID fileId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            agencyOpportunityService.deleteFile(userId, opportunityId, fileId);
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
    @Operation(summary = "Kanban de propostas da agência (filtros + paginação)")
    public Response pipeline(
            @QueryParam("status") String status,
            @QueryParam("consultantId") UUID consultantId,
            @QueryParam("q") String q,
            @QueryParam("scope") @DefaultValue("ACTIVE") String scope,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("100") int size,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            org.example.domain.enums.ProposalStatus parsed = null;
            if (status != null && !status.isBlank()) {
                try {
                    parsed = org.example.domain.enums.ProposalStatus.valueOf(status.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid proposal status: " + status);
                }
            }
            org.example.domain.enums.PipelineScope pipelineScope;
            try {
                pipelineScope = org.example.domain.enums.PipelineScope.fromString(scope);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid pipeline scope: " + scope);
            }
            return Response.ok(proposalService.listPipeline(
                    userId, parsed, consultantId, q, pipelineScope, page, size)).build();
        });
    }

    @GET
    @Path("/trips/picker")
    @Operation(summary = "Listar roteiros da agência para adicionar à proposta (filtro por origem)")
    public Response tripsPicker(
            @QueryParam("origin") @DefaultValue("ALL") String origin,
            @QueryParam("q") String q,
            @QueryParam("excludeProposalId") UUID excludeProposalId,
            @Context HttpHeaders headers) {
        return withUser(headers, userId -> {
            org.example.domain.enums.TripPickerOrigin parsed;
            try {
                parsed = org.example.domain.enums.TripPickerOrigin.fromString(origin);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid origin: " + origin);
            }
            return Response.ok(commercialProposalService.listTripsForPicker(
                    userId, parsed, q, excludeProposalId)).build();
        });
    }

    @GET
    @Path("/analytics")
    @Operation(summary = "BI: conversão, destinos, margem e leaderboard da equipe")
    public Response analytics(
            @Context HttpHeaders headers,
            @QueryParam("period") String period) {
        return withUser(headers, userId ->
                Response.ok(proposalService.analytics(userId, period)).build());
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
