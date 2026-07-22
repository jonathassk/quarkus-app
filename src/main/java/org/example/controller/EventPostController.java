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
import org.example.application.dto.event.CreateEventPostCommentRequestDTO;
import org.example.application.dto.event.CreateEventPostRequestDTO;
import org.example.application.services.TokenService;
import org.example.application.services.event.EventPostService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

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

    @POST
    @Path("/{id}/posts/{postId}/likes")
    @Transactional
    @Operation(summary = "Curtir post")
    public Response likePost(
            @PathParam("id") UUID id, @PathParam("postId") UUID postId, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(eventPostService.likePost(id, postId, userId)).build());
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
