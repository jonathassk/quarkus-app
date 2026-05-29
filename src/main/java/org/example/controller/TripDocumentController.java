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
import org.example.application.services.TokenService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripDocument;
import org.example.domain.entity.User;
import org.example.domain.enums.DocumentStatus;
import org.example.domain.repository.TripDocumentRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.DocumentUploadSupport;
import org.example.utils.RequestAuthHeaders;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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

    @GET
    @Path("/{tripId}/documents")
    @Transactional(Transactional.TxType.REQUIRED)
    public Response listDocuments(
            @PathParam("tripId") Long tripId,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
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
     * Upload via API (Lambda → R2). Avoids browser CORS on presigned PUT to cloudflarestorage.com.
     */
    @POST
    @Path("/{tripId}/documents/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadDocument(
            @PathParam("tripId") Long tripId,
            MultipartFormDataInput multipart,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
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

        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("TRIP_NOT_FOUND").message("Trip not found").build())
                    .build();
        }

        String title = resolveMultipartTitle(multipart, upload.fileName());
        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        String s3Key = "trips/" + tripId + "/documents/" + UUID.randomUUID() + extension;
        User uploader = userRepository.findById(userIdOpt.get());

        try {
            objectStorageService.putObject(s3Key, fileBytes, upload.contentType());

            TripDocument doc = TripDocument.builder()
                    .trip(trip)
                    .title(title.length() > 255 ? title.substring(0, 255) : title)
                    .s3Key(s3Key)
                    .contentType(upload.contentType())
                    .status(DocumentStatus.READY)
                    .uploadedBy(uploader)
                    .build();

            tripDocumentRepository.persist(doc);
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
    public Response uploadRequest(
            @PathParam("tripId") Long tripId,
            UploadDocumentRequest req,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("Upload request unauthorized tripId={}", tripId);
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("Upload request forbidden tripId={} userId={}", tripId, userIdOpt.get());
            return forbiddenResponse();
        }
        if (!objectStorageService.isConfigured()) {
            log.error("Upload request rejected: R2 not configured tripId={} userId={}", tripId, userIdOpt.get());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder()
                            .code("STORAGE_NOT_CONFIGURED")
                            .message("Document storage is not configured")
                            .build())
                    .build();
        }

        if (req == null) {
            log.warn("Upload request missing body tripId={} userId={}", tripId, userIdOpt.get());
            return badRequest("VALIDATION_ERROR", "Request body is required");
        }

        Optional<DocumentUploadSupport.ResolvedUpload> resolved =
                DocumentUploadSupport.resolve(req.getFileName(), req.getContentType());
        if (resolved.isEmpty()) {
            String msg = DocumentUploadSupport.unsupportedTypeMessage(
                    req.getContentType(), req.getFileName());
            log.warn(
                    "Upload request validation failed tripId={} userId={} fileName={} contentType={} — {}",
                    tripId,
                    userIdOpt.get(),
                    req.getFileName(),
                    req.getContentType(),
                    msg);
            return badRequest("UNSUPPORTED_CONTENT_TYPE", msg);
        }

        DocumentUploadSupport.ResolvedUpload upload = resolved.get();

        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            log.warn("Upload request trip not found tripId={} userId={}", tripId, userIdOpt.get());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder()
                            .code("TRIP_NOT_FOUND")
                            .message("Trip not found")
                            .build())
                    .build();
        }

        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        String s3Key = "trips/" + tripId + "/documents/" + UUID.randomUUID() + extension;
        String title = (req.getTitle() != null && !req.getTitle().isBlank())
                ? req.getTitle().trim()
                : upload.fileName();

        User uploader = userRepository.findById(userIdOpt.get());

        try {
            String uploadUrl = objectStorageService.presignPut(s3Key, upload.contentType());

            TripDocument doc = TripDocument.builder()
                    .trip(trip)
                    .title(title.length() > 255 ? title.substring(0, 255) : title)
                    .s3Key(s3Key)
                    .contentType(upload.contentType())
                    .status(DocumentStatus.PENDING)
                    .uploadedBy(uploader)
                    .build();

            tripDocumentRepository.persist(doc);

            UploadDocumentResponse body = UploadDocumentResponse.builder()
                    .documentId(doc.id)
                    .uploadUrl(uploadUrl)
                    .s3Key(s3Key)
                    .expiresInSeconds(objectStorageService.getUploadPresignSeconds())
                    .build();

            return Response.status(Response.Status.CREATED).entity(body).build();
        } catch (Exception e) {
            log.error(
                    "Upload request failed tripId={} userId={} fileName={} contentType={}",
                    tripId,
                    userIdOpt.get(),
                    upload.fileName(),
                    upload.contentType(),
                    e);
            return mapUploadException(e);
        }
    }

    @POST
    @Path("/{tripId}/documents/upload-confirm")
    @Transactional
    public Response uploadConfirm(
            @PathParam("tripId") Long tripId,
            ConfirmUploadRequest req,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("Upload confirm unauthorized tripId={}", tripId);
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("Upload confirm forbidden tripId={} userId={}", tripId, userIdOpt.get());
            return forbiddenResponse();
        }

        if (req == null || req.getDocumentId() == null) {
            log.warn("Upload confirm missing documentId tripId={} userId={}", tripId, userIdOpt.get());
            return badRequest("VALIDATION_ERROR", "documentId is required");
        }

        Optional<TripDocument> docOpt =
                tripDocumentRepository.findByIdAndTripId(req.getDocumentId(), tripId);
        if (docOpt.isEmpty()) {
            log.warn(
                    "Upload confirm document not found tripId={} documentId={} userId={}",
                    tripId,
                    req.getDocumentId(),
                    userIdOpt.get());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder()
                            .code("DOCUMENT_NOT_FOUND")
                            .message("Document not found")
                            .build())
                    .build();
        }

        TripDocument doc = docOpt.get();
        if (doc.getStatus() != DocumentStatus.PENDING) {
            log.warn(
                    "Upload confirm invalid status tripId={} documentId={} status={} userId={}",
                    tripId,
                    req.getDocumentId(),
                    doc.getStatus(),
                    userIdOpt.get());
            return badRequest("INVALID_DOCUMENT_STATUS", "Document is not pending confirmation");
        }

        try {
            doc.setStatus(DocumentStatus.READY);
            return Response.ok(toResponse(doc)).build();
        } catch (Exception e) {
            log.error(
                    "Upload confirm failed tripId={} documentId={} userId={}",
                    tripId,
                    req.getDocumentId(),
                    userIdOpt.get(),
                    e);
            return serverError("Failed to confirm document upload");
        }
    }

    @DELETE
    @Path("/{tripId}/documents/{docId}")
    @Transactional
    public Response deleteDocument(
            @PathParam("tripId") Long tripId,
            @PathParam("docId") Long docId,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
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
        try {
            if (objectStorageService.isConfigured() && doc.getS3Key() != null && !doc.getS3Key().isBlank()) {
                objectStorageService.deleteObject(doc.getS3Key());
            }
            tripDocumentRepository.delete(doc);
            log.info("Document deleted tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Delete document failed tripId={} docId={} userId={}", tripId, docId, userIdOpt.get(), e);
            return serverError("Failed to delete document");
        }
    }

    @GET
    @Path("/{tripId}/documents/{docId}/view-request")
    @Transactional(Transactional.TxType.REQUIRED)
    public Response viewRequest(
            @PathParam("tripId") Long tripId,
            @PathParam("docId") Long docId,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            log.warn("View request unauthorized tripId={} docId={}", tripId, docId);
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("View request forbidden tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return forbiddenResponse();
        }
        if (!objectStorageService.isConfigured()) {
            log.error("View request rejected: R2 not configured tripId={} docId={}", tripId, docId);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder()
                            .code("STORAGE_NOT_CONFIGURED")
                            .message("Document storage is not configured")
                            .build())
                    .build();
        }

        Optional<TripDocument> docOpt = tripDocumentRepository.findByIdAndTripId(docId, tripId);
        if (docOpt.isEmpty()) {
            log.warn("View request document not found tripId={} docId={} userId={}", tripId, docId, userIdOpt.get());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder()
                            .code("DOCUMENT_NOT_FOUND")
                            .message("Document not found")
                            .build())
                    .build();
        }

        TripDocument doc = docOpt.get();
        if (doc.getStatus() != DocumentStatus.READY) {
            log.warn(
                    "View request document not ready tripId={} docId={} status={}",
                    tripId,
                    docId,
                    doc.getStatus());
            return badRequest("DOCUMENT_NOT_READY", "Document is not ready for viewing");
        }

        try {
            String viewUrl = objectStorageService.presignGet(doc.getS3Key());

            ViewDocumentResponse body = ViewDocumentResponse.builder()
                    .documentId(doc.id)
                    .viewUrl(viewUrl)
                    .contentType(doc.getContentType())
                    .title(doc.getTitle())
                    .expiresInSeconds(objectStorageService.getViewPresignSeconds())
                    .build();

            return Response.ok(body).build();
        } catch (Exception e) {
            log.error("View request presign failed tripId={} docId={} s3Key={}", tripId, docId, doc.getS3Key(), e);
            return serverError("Failed to generate view URL");
        }
    }

    private Optional<Long> resolveAuthenticatedUserId(HttpHeaders headers) {
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
            Long userId = Long.valueOf(tokenService.validateToken(token));
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

    private TripDocumentResponse toResponse(TripDocument doc) {
        return TripDocumentResponse.builder()
                .id(doc.id)
                .tripId(doc.getTrip().id)
                .title(doc.getTitle())
                .contentType(doc.getContentType())
                .status(doc.getStatus().name())
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
