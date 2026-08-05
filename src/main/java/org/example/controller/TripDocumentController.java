package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.dto.document.*;
import org.example.application.services.B2bAuditService;
import org.example.application.services.TokenService;
import org.example.application.services.entitlement.EntitlementService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripDocument;
import org.example.domain.entity.User;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.DocumentStatus;
import org.example.domain.enums.DocumentVisibility;
import org.example.domain.repository.TripDocumentRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.crypto.DocumentCryptoService;
import org.example.infrastructure.storage.DocumentViewAuditService;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.DocumentUploadSupport;
import org.example.utils.RequestAuthHeaders;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Tag(name = "Trip Documents", description = "Gerenciamento de arquivos e documentos anexados aos roteiros de viagem (R2/S3, criptografados)")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripDocumentController {

    private final TripRepository tripRepository;
    private final TripDocumentRepository tripDocumentRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final ObjectStorageService objectStorageService;
    private final DocumentCryptoService documentCryptoService;
    private final DocumentViewAuditService documentViewAuditService;
    private final B2bAuditService auditService;
    private final EntitlementService entitlementService;

    @GET
    @Path("/{tripId}/documents")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
        summary = "Listar documentos de uma viagem",
        description = "Retorna todos os documentos prontos (READY) vinculados a uma viagem específica."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de documentos retornada com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem")
    })
    public Response listDocuments(
            @PathParam("tripId") UUID tripId,
            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("List documents unauthorized tripId={}", tripId);
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("List documents forbidden tripId={} userId={}", tripId, userIdOpt.get());
            return forbiddenResponse();
        }

        try {
            List<TripDocumentResponse> docs = tripDocumentRepository.findByTripId(tripId).stream()
                    .filter(d -> d.getStatus() == DocumentStatus.READY)
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return Response.ok(docs).build();
        } catch (Exception e) {
            log.error("List documents failed tripId={} userId={}", tripId, userIdOpt.get(), e);
            return serverError("Failed to list documents");
        }
    }

    /**
     * Upload via API (Lambda → encrypt → R2). Avoids browser CORS on R2 and keeps plaintext off the bucket.
     */
    @POST
    @Path("/{tripId}/documents/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @Operation(
        summary = "Enviar arquivo diretamente (API)",
        description = "Faz o upload de um documento em formato multipart/form-data. O arquivo é criptografado (AES-256-GCM) antes de ir para o R2. Limite de 10 MB."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Documento enviado e criado com sucesso"),
        @APIResponse(responseCode = "400", description = "Arquivo ausente, vazio, maior que 10MB ou tipo não suportado"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada"),
        @APIResponse(responseCode = "503", description = "Serviço de storage ou criptografia não configurado")
    })
    public Response uploadDocument(
            @PathParam("tripId") UUID tripId,
            MultipartFormDataInput multipart,
            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            return forbiddenResponse();
        }
        if (!objectStorageService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder()
                            .code("STORAGE_NOT_CONFIGURED")
                            .message("Document storage is not configured")
                            .build())
                    .build();
        }
        if (!documentCryptoService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder()
                            .code("ENCRYPTION_NOT_CONFIGURED")
                            .message("Document encryption is not configured")
                            .build())
                    .build();
        }

        List<InputPart> fileParts = multipart.getFormDataMap().get("file");
        if (fileParts == null || fileParts.isEmpty()) {
            return badRequest("VALIDATION_ERROR", "file is required");
        }

        InputPart filePart = fileParts.getFirst();
        String rawFileName = extractMultipartFileName(filePart);
        String browserContentType = filePart.getMediaType() != null ? filePart.getMediaType().toString() : null;

        Optional<DocumentUploadSupport.ResolvedUpload> resolved =
                DocumentUploadSupport.resolve(rawFileName, browserContentType);
        if (resolved.isEmpty()) {
            return badRequest(
                    "UNSUPPORTED_CONTENT_TYPE",
                    DocumentUploadSupport.unsupportedTypeMessage(browserContentType, rawFileName));
        }

        DocumentUploadSupport.ResolvedUpload upload = resolved.get();
        byte[] fileBytes;
        try {
            fileBytes = filePart.getBody(byte[].class, null);
        } catch (IOException e) {
            log.error("Multipart read failed tripId={} userId={}", tripId, userIdOpt.get(), e);
            return badRequest("VALIDATION_ERROR", "Could not read uploaded file");
        }

        if (fileBytes.length == 0) {
            return badRequest("VALIDATION_ERROR", "File is empty");
        }
        if (fileBytes.length > DocumentUploadSupport.MAX_UPLOAD_BYTES) {
            return badRequest("FILE_TOO_LARGE", "File exceeds 10 MB limit");
        }

        entitlementService.requireCanUploadDocument(userIdOpt.get(), tripId, fileBytes.length);

        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("TRIP_NOT_FOUND").message("Trip not found").build())
                    .build();
        }

        String title = resolveMultipartTitle(multipart, upload.fileName());
        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        String s3Key = "trips/" + tripId + "/documents/" + UUID.randomUUID() + extension + ".enc";
        User uploader = userRepository.findById(userIdOpt.get());

        try {
            byte[] encrypted = documentCryptoService.encrypt(fileBytes);
            // Content-Type no R2 é opaco — o MIME real fica só no banco.
            objectStorageService.putObject(s3Key, encrypted, "application/octet-stream");

            TripDocument doc = TripDocument.builder()
                    .trip(trip)
                    .title(title.length() > 255 ? title.substring(0, 255) : title)
                    .s3Key(s3Key)
                    .contentType(upload.contentType())
                    .sizeBytes((long) fileBytes.length)
                    .encryptionVersion(documentCryptoService.currentVersion())
                    .status(DocumentStatus.READY)
                    .uploadedBy(uploader)
                    .build();

            tripDocumentRepository.persist(doc);

            auditService.record(
                    trip, userIdOpt.get(),
                    B2bTripLogAction.DOCUMENT_UPLOADED,
                    "DOCUMENT", doc.id,
                    "Documento enviado: '" + doc.getTitle() + "'");

            return Response.status(Response.Status.CREATED).entity(toResponse(doc)).build();
        } catch (Exception e) {
            log.error(
                    "Direct upload failed tripId={} userId={} fileName={}",
                    tripId,
                    userIdOpt.get(),
                    upload.fileName(),
                    e);
            return mapUploadException(e);
        }
    }

    @POST
    @Path("/{tripId}/documents/upload-request")
    @Transactional
    @Operation(
        summary = "Solicitar upload presignado (descontinuado)",
        description = "Descontinuado: upload direto ao R2 deixaria o arquivo em claro. Use POST /documents/upload."
    )
    @APIResponses({
        @APIResponse(responseCode = "410", description = "Endpoint descontinuado — use multipart /upload")
    })
    public Response uploadRequest(
            @PathParam("tripId") UUID tripId,
            @RequestBody(description = "Ignorado", required = false) UploadDocumentRequest req,
            @Context HttpHeaders headers) {
        return Response.status(Response.Status.GONE)
                .entity(ApiErrorBody.builder()
                        .code("UPLOAD_REQUEST_DEPRECATED")
                        .message("Presigned document upload is disabled. Use POST /api/v1/trips/{tripId}/documents/upload so files are encrypted before storage.")
                        .build())
                .build();
    }

    @POST
    @Path("/{tripId}/documents/upload-confirm")
    @Transactional
    @Operation(
        summary = "Confirmar upload presignado (descontinuado)",
        description = "Descontinuado junto com upload-request."
    )
    @APIResponses({
        @APIResponse(responseCode = "410", description = "Endpoint descontinuado")
    })
    public Response uploadConfirm(
            @PathParam("tripId") UUID tripId,
            @RequestBody(description = "Ignorado", required = false) ConfirmUploadRequest req,
            @Context HttpHeaders headers) {
        return Response.status(Response.Status.GONE)
                .entity(ApiErrorBody.builder()
                        .code("UPLOAD_CONFIRM_DEPRECATED")
                        .message("Presigned document upload is disabled. Use POST /api/v1/trips/{tripId}/documents/upload.")
                        .build())
                .build();
    }

    @DELETE
    @Path("/{tripId}/documents/{docId}")
    @Transactional
    @Operation(
        summary = "Excluir documento",
        description = "Exclui permanentemente um documento de uma viagem e remove o arquivo correspondente do R2."
    )
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Documento excluído com sucesso (No Content)"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem"),
        @APIResponse(responseCode = "404", description = "Documento não encontrado")
    })
    public Response deleteDocument(
            @PathParam("tripId") UUID tripId,
            @PathParam("docId") UUID docId,
            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("Delete document unauthorized tripId={} docId={}", tripId, docId);
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("Delete document forbidden tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return forbiddenResponse();
        }

        Optional<TripDocument> docOpt = tripDocumentRepository.findByIdAndTripId(docId, tripId);
        if (docOpt.isEmpty()) {
            log.warn("Delete document not found tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder()
                            .code("DOCUMENT_NOT_FOUND")
                            .message("Document not found")
                            .build())
                    .build();
        }

        TripDocument doc = docOpt.get();
        String docTitle = doc.getTitle();
        Trip trip = doc.getTrip();

        try {
            if (objectStorageService.isConfigured() && doc.getS3Key() != null && !doc.getS3Key().isBlank()) {
                objectStorageService.deleteObject(doc.getS3Key());
            }
            tripDocumentRepository.delete(doc);

            auditService.record(
                    trip, userIdOpt.get(),
                    B2bTripLogAction.DOCUMENT_DELETED,
                    "DOCUMENT", docId,
                    "Documento excluído: '" + docTitle + "'");

            log.info("Document deleted tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Delete document failed tripId={} docId={} userId={}", tripId, docId, userIdOpt.get(), e);
            return serverError("Failed to delete document");
        }
    }

    /**
     * Entrega o documento descriptografado via API autenticada (sem URL presignada em claro no R2).
     * Registra auditoria de visualização no R2 (retenção: fim da viagem + 3 meses).
     */
    @GET
    @Path("/{tripId}/documents/{docId}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
        summary = "Baixar/visualizar conteúdo do documento",
        description = "Retorna o arquivo descriptografado após autenticação e vínculo à viagem. Registra quem visualizou."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Conteúdo do documento"),
        @APIResponse(responseCode = "400", description = "Documento não pronto"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido"),
        @APIResponse(responseCode = "404", description = "Documento não encontrado"),
        @APIResponse(responseCode = "503", description = "Storage/criptografia indisponível")
    })
    public Response viewContent(
            @PathParam("tripId") UUID tripId,
            @PathParam("docId") UUID docId,
            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("View content unauthorized tripId={} docId={}", tripId, docId);
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("View content forbidden tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return forbiddenResponse();
        }
        if (!objectStorageService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder()
                            .code("STORAGE_NOT_CONFIGURED")
                            .message("Document storage is not configured")
                            .build())
                    .build();
        }

        Optional<TripDocument> docOpt = tripDocumentRepository.findByIdAndTripId(docId, tripId);
        if (docOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder()
                            .code("DOCUMENT_NOT_FOUND")
                            .message("Document not found")
                            .build())
                    .build();
        }

        TripDocument doc = docOpt.get();
        if (doc.getStatus() != DocumentStatus.READY) {
            return badRequest("DOCUMENT_NOT_READY", "Document is not ready for viewing");
        }
        if (doc.getEncryptionVersion() >= DocumentCryptoService.ENCRYPTION_VERSION_AES_GCM
                && !documentCryptoService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder()
                            .code("ENCRYPTION_NOT_CONFIGURED")
                            .message("Document encryption is not configured")
                            .build())
                    .build();
        }

        try {
            byte[] stored = objectStorageService.getObjectBytes(doc.getS3Key());
            byte[] plaintext = documentCryptoService.decrypt(stored, doc.getEncryptionVersion());

            documentViewAuditService.recordView(
                    doc.getTrip(),
                    doc.id,
                    userIdOpt.get(),
                    doc.getTitle(),
                    resolveClientIp(headers));

            String safeName = doc.getTitle() != null ? doc.getTitle().replace("\"", "") : "document";
            String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");

            return Response.ok(plaintext)
                    .type(doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "inline; filename=\"" + safeName + "\"; filename*=UTF-8''" + encoded)
                    .header("X-Document-Id", doc.id.toString())
                    .header("X-Document-Title", safeName)
                    .header("Cache-Control", "private, no-store")
                    .build();
        } catch (Exception e) {
            log.error("View content failed tripId={} docId={} s3Key={}", tripId, docId, doc.getS3Key(), e);
            return serverError("Failed to load document");
        }
    }

    @GET
    @Path("/{tripId}/documents/{docId}/view-request")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
        summary = "Metadados para visualização (sem URL R2)",
        description = "Retorna contentType/title e indica que o binário deve ser obtido em GET .../content (criptografado em repouso)."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Metadados de visualização"),
        @APIResponse(responseCode = "400", description = "Documento não pronto"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido"),
        @APIResponse(responseCode = "404", description = "Documento não encontrado")
    })
    public Response viewRequest(
            @PathParam("tripId") UUID tripId,
            @PathParam("docId") UUID docId,
            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            return forbiddenResponse();
        }

        Optional<TripDocument> docOpt = tripDocumentRepository.findByIdAndTripId(docId, tripId);
        if (docOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder()
                            .code("DOCUMENT_NOT_FOUND")
                            .message("Document not found")
                            .build())
                    .build();
        }

        TripDocument doc = docOpt.get();
        if (doc.getStatus() != DocumentStatus.READY) {
            return badRequest("DOCUMENT_NOT_READY", "Document is not ready for viewing");
        }

        // Sem URL presignada: o cliente deve chamar /content com o JWT.
        ViewDocumentResponse body = ViewDocumentResponse.builder()
                .documentId(doc.id)
                .viewUrl("/api/v1/trips/" + tripId + "/documents/" + docId + "/content")
                .contentType(doc.getContentType())
                .title(doc.getTitle())
                .expiresInSeconds(0)
                .build();
        return Response.ok(body).build();
    }

    private Optional<UUID> resolveAuthenticatedUserId(HttpHeaders headers) {
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
                log.warn("Document auth: user not found userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Document auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String resolveClientIp(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = headers.getHeaderString("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : null;
    }

    private TripDocumentResponse toResponse(TripDocument doc) {
        return TripDocumentResponse.builder()
                .id(doc.id)
                .tripId(doc.getTrip().id)
                .title(doc.getTitle())
                .contentType(doc.getContentType())
                .status(doc.getStatus().name())
                .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : DocumentVisibility.CLIENT.name())
                .activityId(doc.getActivity() != null ? doc.getActivity().id : null)
                .segmentId(doc.getSegment() != null ? doc.getSegment().id : null)
                .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                .build();
    }

    private Response unauthorizedResponse() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiErrorBody.builder()
                        .code("UNAUTHORIZED")
                        .message("Invalid or expired token")
                        .build())
                .build();
    }

    private Response forbiddenResponse() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ApiErrorBody.builder()
                        .code("FORBIDDEN")
                        .message("You do not have access to this trip")
                        .build())
                .build();
    }

    private Response badRequest(String code, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiErrorBody.builder().code(code).message(message).build())
                .build();
    }

    private Response serverError(String message) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiErrorBody.builder().code("INTERNAL_ERROR").message(message).build())
                .build();
    }

    private Response mapUploadException(Exception e) {
        return serverError(
                e.getMessage() != null ? e.getMessage() : "Failed to prepare document upload");
    }

    private static String resolveMultipartTitle(MultipartFormDataInput multipart, String defaultTitle) {
        List<InputPart> titleParts = multipart.getFormDataMap().get("title");
        if (titleParts == null || titleParts.isEmpty()) {
            return defaultTitle;
        }
        try {
            String title = titleParts.getFirst().getBodyAsString();
            if (title != null && !title.isBlank()) {
                return title.trim();
            }
        } catch (IOException ignored) {
            // use default
        }
        return defaultTitle;
    }

    private static String extractMultipartFileName(InputPart filePart) {
        MultivaluedMap<String, String> headers = filePart.getHeaders();
        String disposition = headers.getFirst("Content-Disposition");
        if (disposition != null) {
            for (String part : disposition.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("filename=")) {
                    String name = trimmed.substring("filename=".length()).trim();
                    if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
                        name = name.substring(1, name.length() - 1);
                    }
                    if (!name.isBlank()) {
                        return name;
                    }
                }
            }
        }
        return "document";
    }
}
