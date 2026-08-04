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
import org.example.application.dto.document.UploadDocumentRequest;
import org.example.application.dto.event.CreateEventPostCommentRequestDTO;
import org.example.application.dto.event.CreateEventPostRequestDTO;
import org.example.application.dto.event.UpdateEventPostRequestDTO;
import org.example.application.dto.event.VoteEventPostPollRequestDTO;
import org.example.application.services.TokenService;
import org.example.application.services.event.EventAuthorizationService;
import org.example.application.services.event.EventPostService;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.utils.DocumentUploadSupport;
import org.example.utils.RequestAuthHeaders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Event Posts", description = "Timeline de eventos")
@Path("/api/v1/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class EventPostController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final EventPostService eventPostService;
    private final EventAuthorizationService authorizationService;
    private final ObjectStorageService objectStorageService;

    @GET
    @Path("/{id}/posts")
    @Transactional
    @Operation(summary = "Listar posts do evento")
    public Response listPosts(
            @PathParam("id") UUID id,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("nextToken") String nextToken,
            @Context HttpHeaders headers) {
        return withAuth(
                headers, userId -> Response.ok(eventPostService.listPosts(id, userId, limit, nextToken)).build());
    }

    @POST
    @Path("/{id}/posts/image-upload-request")
    @Transactional
    @Operation(
            summary = "Solicitar upload de imagem do post",
            description = "Gera URL presignada R2 para imagem da timeline. Apenas imagens (JPEG, PNG, WebP, GIF).")
    public Response postImageUploadRequest(
            @PathParam("id") UUID id, UploadDocumentRequest req, @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }

        authorizationService.assertCanPost(id, userId.get());

        if (!objectStorageService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("code", "STORAGE_NOT_CONFIGURED", "message", "Document storage is not configured"))
                    .build();
        }
        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "VALIDATION_ERROR", "message", "Request body is required"))
                    .build();
        }

        Optional<DocumentUploadSupport.ResolvedUpload> resolved =
                DocumentUploadSupport.resolve(req.getFileName(), req.getContentType());
        if (resolved.isEmpty()) {
            String msg = DocumentUploadSupport.unsupportedTypeMessage(req.getContentType(), req.getFileName());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "UNSUPPORTED_CONTENT_TYPE", "message", msg))
                    .build();
        }

        DocumentUploadSupport.ResolvedUpload upload = resolved.get();
        if (!upload.contentType().startsWith("image/")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "code",
                            "UNSUPPORTED_CONTENT_TYPE",
                            "message",
                            "Only image uploads are allowed for event posts (JPEG, PNG, WebP, GIF)"))
                    .build();
        }

        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        String s3Key =
                "events/" + id + "/posts/" + userId.get() + "/" + UUID.randomUUID() + extension;

        try {
            String uploadUrl = objectStorageService.presignPut(s3Key, upload.contentType());
            String publicUrl = objectStorageService.getPublicUrl(s3Key);
            log.info(
                    "POST /events/{}/posts/image-upload-request 201 userId={} s3Key={}",
                    id,
                    userId.get(),
                    s3Key);
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of(
                            "uploadUrl", uploadUrl,
                            "s3Key", s3Key,
                            "publicUrl", publicUrl,
                            "expiresInSeconds", objectStorageService.getUploadPresignSeconds()))
                    .build();
        } catch (Exception e) {
            log.error("Event post image upload request failed eventId={} userId={}", id, userId.get(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "code",
                            "INTERNAL_ERROR",
                            "message",
                            "Erro ao gerar URL presignada: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{id}/posts")
    @Transactional
    @Operation(summary = "Criar post no evento")
    public Response createPost(
            @PathParam("id") UUID id, CreateEventPostRequestDTO body, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId ->
                        Response.status(Response.Status.CREATED)
                                .entity(eventPostService.createPost(id, body, userId))
                                .build());
    }

    @DELETE
    @Path("/{id}/posts/{postId}")
    @Transactional
    @Operation(summary = "Deletar post")
    public Response deletePost(
            @PathParam("id") UUID id, @PathParam("postId") UUID postId, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    eventPostService.deletePost(id, postId, userId);
                    return Response.noContent().build();
                });
    }

    @PATCH
    @Path("/{id}/posts/{postId}")
    @Transactional
    @Operation(summary = "Atualizar post (fixar / desfixar)")
    public Response updatePost(
            @PathParam("id") UUID id,
            @PathParam("postId") UUID postId,
            UpdateEventPostRequestDTO body,
            @Context HttpHeaders headers) {
        return withAuth(
                headers, userId -> Response.ok(eventPostService.updatePost(id, postId, body, userId)).build());
    }

    @POST
    @Path("/{id}/posts/{postId}/likes")
    @Transactional
    @Operation(summary = "Curtir post")
    public Response likePost(
            @PathParam("id") UUID id, @PathParam("postId") UUID postId, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventPostService.likePost(id, postId, userId)).build());
    }

    @POST
    @Path("/{id}/posts/{postId}/poll/votes")
    @Transactional
    @Operation(summary = "Votar em enquete do post")
    public Response votePoll(
            @PathParam("id") UUID id,
            @PathParam("postId") UUID postId,
            VoteEventPostPollRequestDTO body,
            @Context HttpHeaders headers) {
        return withAuth(
                headers, userId -> Response.ok(eventPostService.votePoll(id, postId, body, userId)).build());
    }

    @GET
    @Path("/{id}/posts/{postId}/comments")
    @Transactional
    @Operation(summary = "Listar comentários")
    public Response listComments(
            @PathParam("id") UUID id, @PathParam("postId") UUID postId, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventPostService.listComments(id, postId, userId)).build());
    }

    @POST
    @Path("/{id}/posts/{postId}/comments")
    @Transactional
    @Operation(summary = "Criar comentário")
    public Response createComment(
            @PathParam("id") UUID id,
            @PathParam("postId") UUID postId,
            CreateEventPostCommentRequestDTO body,
            @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId ->
                        Response.status(Response.Status.CREATED)
                                .entity(eventPostService.createComment(id, postId, body, userId))
                                .build());
    }

    @DELETE
    @Path("/{id}/posts/{postId}/comments/{commentId}")
    @Transactional
    @Operation(summary = "Deletar comentário")
    public Response deleteComment(
            @PathParam("id") UUID id,
            @PathParam("postId") UUID postId,
            @PathParam("commentId") UUID commentId,
            @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    eventPostService.deleteComment(id, postId, commentId, userId);
                    return Response.noContent().build();
                });
    }

    private Response withAuth(HttpHeaders headers, java.util.function.Function<UUID, Response> action) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        return action.apply(userId.get());
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
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Event post auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(java.util.Map.of("code", "UNAUTHORIZED"))
                .build();
    }
}
