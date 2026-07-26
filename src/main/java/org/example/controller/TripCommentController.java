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
import org.example.application.dto.trip.request.CreateTripCommentRequest;
import org.example.application.services.TokenService;
import org.example.application.services.trip.TripCommentService;
import org.example.domain.enums.TripCommentTargetType;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Trip Comments", description = "Comentários no dia/atividade da viagem")
@Path("/api/v1/trips/{tripId}/comments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripCommentController {

    private final TripCommentService commentService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GET
    @Operation(summary = "Listar comentários (VIEWER+). Marca como lidos por padrão.")
    public Response list(
            @PathParam("tripId") UUID tripId,
            @QueryParam("targetType") TripCommentTargetType targetType,
            @QueryParam("targetId") String targetId,
            @QueryParam("markRead") @DefaultValue("true") boolean markRead,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.ok(
                            commentService.list(tripId, actorId.get(), targetType, targetId, markRead))
                    .build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        }
    }

    @GET
    @Path("/unread-count")
    @Operation(summary = "Contador de comentários não lidos")
    public Response unreadCount(@PathParam("tripId") UUID tripId, @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            long count = commentService.unreadCount(tripId, actorId.get());
            return Response.ok(Map.of("unreadCount", count)).build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        }
    }

    @POST
    @Transactional
    @Operation(summary = "Criar comentário (VIEWER+)")
    public Response create(
            @PathParam("tripId") UUID tripId,
            CreateTripCommentRequest body,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(commentService.create(tripId, actorId.get(), body))
                    .build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        } catch (BadRequestException e) {
            return error(Response.Status.BAD_REQUEST, "BAD_REQUEST", e);
        }
    }

    @POST
    @Path("/{commentId}/resolve")
    @Transactional
    @Operation(summary = "Marcar comentário como resolvido (ADMIN+)")
    public Response resolve(
            @PathParam("tripId") UUID tripId,
            @PathParam("commentId") UUID commentId,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            return Response.ok(commentService.resolve(tripId, commentId, actorId.get())).build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        }
    }

    @DELETE
    @Path("/{commentId}")
    @Transactional
    @Operation(summary = "Remover comentário (autor ou ADMIN+)")
    public Response delete(
            @PathParam("tripId") UUID tripId,
            @PathParam("commentId") UUID commentId,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return unauthorized();
        }
        try {
            commentService.delete(tripId, commentId, actorId.get());
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND", e);
        } catch (ForbiddenException e) {
            return error(Response.Status.FORBIDDEN, "FORBIDDEN", e);
        }
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
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                .build();
    }

    private Response error(Response.Status status, String code, Exception e) {
        return Response.status(status)
                .entity(ApiErrorBody.builder().code(code).message(e.getMessage()).build())
                .build();
    }
}
