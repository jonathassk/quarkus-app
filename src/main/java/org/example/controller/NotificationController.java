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
import org.example.application.dto.notification.MarkNotificationsReadRequest;
import org.example.application.dto.notification.MarkNotificationsReadResponse;
import org.example.application.dto.notification.NotificationsPageDTO;
import org.example.application.services.TokenService;
import org.example.application.services.notification.NotificationService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Notifications", description = "Central de notificações in-app")
@Path("/api/v1/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GET
    @Operation(summary = "Listar notificações do usuário autenticado (paginado)")
    public Response list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly,
            @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUserId(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        if (size < 1 || size > 50) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder()
                            .code("VALIDATION_ERROR")
                            .message("size must be between 1 and 50")
                            .build())
                    .build();
        }
        if (page < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder()
                            .code("VALIDATION_ERROR")
                            .message("page must be >= 0")
                            .build())
                    .build();
        }
        NotificationsPageDTO body = notificationService.list(userId.get(), page, size, unreadOnly);
        return Response.ok(body).build();
    }

    @POST
    @Path("/read")
    @Transactional
    @Operation(summary = "Marcar notificações como lidas (ids ou all)")
    public Response markRead(MarkNotificationsReadRequest body, @Context HttpHeaders headers) {
        Optional<UUID> userId = resolveUserId(headers);
        if (userId.isEmpty()) {
            return unauthorized();
        }
        try {
            MarkNotificationsReadResponse result = notificationService.markRead(userId.get(), body);
            return Response.ok(result).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder()
                            .code("VALIDATION_ERROR")
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiErrorBody.builder()
                        .code("UNAUTHORIZED")
                        .message("Invalid or expired token")
                        .build())
                .build();
    }

    private Optional<UUID> resolveUserId(HttpHeaders headers) {
        String bearerLine =
                RequestAuthHeaders.resolveBearerHeaderLine(
                        headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null,
                        headers != null
                                ? headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION)
                                : null);
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
            log.warn("Notifications auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
