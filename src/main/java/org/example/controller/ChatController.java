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
import org.example.application.dto.chat.*;
import org.example.application.services.TokenService;
import org.example.application.services.chat.DirectChatService;
import org.example.application.services.chat.ChatWsTokenService;
import org.example.application.services.chat.InboxService;
import org.example.application.services.chat.MessageService;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Tag(name = "Chat", description = "Chat Baggagi — conversas, mensagens e inbox")
@Path("/api/v1/chat")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ChatController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final InboxService inboxService;
    private final MessageService messageService;
    private final DirectChatService directChatService;
    private final ChatWsTokenService chatWsTokenService;

    @GET
    @Path("/conversations")
    @Transactional
    @Operation(summary = "Inbox global de conversas")
    public Response getInbox(
            @QueryParam("limit") Integer limit,
            @QueryParam("cursor") String cursor,
            @Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(inboxService.getInbox(userId, limit, cursor)).build());
    }

    @GET
    @Path("/conversations/{id}")
    @Transactional
    @Operation(summary = "Detalhe de uma conversa")
    public Response getConversation(@PathParam("id") String id, @Context HttpHeaders headers) {
        UUID conversationId = parseUuid(id);
        return withAuth(
                headers,
                userId -> Response.ok(inboxService.getConversationDetail(conversationId, userId)).build());
    }

    @GET
    @Path("/conversations/{id}/messages")
    @Transactional
    @Operation(summary = "Histórico paginado de mensagens")
    public Response getMessages(
            @PathParam("id") String id,
            @QueryParam("limit") Integer limit,
            @QueryParam("before") String before,
            @Context HttpHeaders headers) {
        UUID conversationId = parseUuid(id);
        return withAuth(
                headers,
                userId ->
                        Response.ok(messageService.getMessages(conversationId, userId, limit, before))
                                .build());
    }

    @POST
    @Path("/conversations/{id}/messages")
    @Transactional
    @Operation(summary = "Enviar mensagem")
    public Response sendMessage(
            @PathParam("id") String id, SendMessageRequestDTO request, @Context HttpHeaders headers) {
        UUID conversationId = parseUuid(id);
        return withAuth(
                headers,
                userId ->
                        Response.status(Response.Status.CREATED)
                                .entity(messageService.sendMessage(conversationId, userId, request))
                                .build());
    }

    @POST
    @Path("/conversations/{id}/read")
    @Transactional
    @Operation(summary = "Marcar conversa como lida")
    public Response markRead(
            @PathParam("id") String id, MarkReadRequestDTO request, @Context HttpHeaders headers) {
        UUID conversationId = parseUuid(id);
        return withAuth(
                headers,
                userId -> {
                    messageService.markAsRead(conversationId, userId, request);
                    return Response.noContent().build();
                });
    }

    @POST
    @Path("/direct")
    @Transactional
    @Operation(summary = "Criar ou abrir chat direto")
    public Response createDirect(CreateDirectChatRequestDTO request, @Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> {
                    if (request == null || request.getTargetUserId() == null) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity("targetUserId is required")
                                .build();
                    }
                    ConversationInboxItemDTO conversation =
                            directChatService.createOrGetDirect(userId, request.getTargetUserId());
                    return Response.status(Response.Status.CREATED).entity(conversation).build();
                });
    }

    @GET
    @Path("/ws-token")
    @Operation(summary = "Token curto para conexão WebSocket (TTL 60s)")
    public Response getWsToken(@Context HttpHeaders headers) {
        return withAuth(
                headers,
                userId -> Response.ok(chatWsTokenService.issueToken(userId)).build());
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
            log.warn("Chat auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Invalid or expired token")
                .build();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid conversation id");
        }
    }
}
