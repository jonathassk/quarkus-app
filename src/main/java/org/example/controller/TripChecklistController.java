package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.checklist.*;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.services.B2bAuditService;
import org.example.application.services.TokenService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripChecklistItem;
import org.example.domain.entity.User;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.repository.TripChecklistItemRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Trip Checklist", description = "Gerenciamento de tarefas (checklist) em roteiros de viagem")
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripChecklistController {

    private final TripRepository tripRepository;
    private final TripChecklistItemRepository checklistRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final B2bAuditService auditService;

    @GET
    @Path("/{tripId}/checklist")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
        summary = "Listar itens de checklist",
        description = "Retorna todos os itens de checklist vinculados a uma viagem específica."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de itens de checklist retornada com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem")
    })
    public Response listItems(
            @PathParam("tripId") Long tripId,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            return forbiddenResponse();
        }

        try {
            List<TripChecklistItemResponse> items =
                    checklistRepository.findByTripId(tripId).stream()
                            .map(this::toResponse)
                            .collect(Collectors.toList());
            return Response.ok(items).build();
        } catch (Exception e) {
            log.error("List checklist failed tripId={}", tripId, e);
            return serverError("Failed to list checklist items");
        }
    }

    @POST
    @Path("/{tripId}/checklist")
    @Transactional
    @Operation(
        summary = "Criar item de checklist",
        description = "Adiciona um novo item de checklist em uma viagem específica."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Item criado com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem"),
        @APIResponse(responseCode = "404", description = "Viagem não encontrada")
    })
    public Response createItem(
            @PathParam("tripId") Long tripId,
            @RequestBody(description = "Dados para criação do item", required = true) CreateTripChecklistItemRequest body,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            return forbiddenResponse();
        }

        String title = body != null && body.getTitle() != null ? body.getTitle().trim() : "";
        if (title.isEmpty()) {
            return badRequest("INVALID_TITLE", "Title is required");
        }
        if (title.length() > 500) {
            return badRequest("INVALID_TITLE", "Title must be at most 500 characters");
        }

        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message("Trip not found").build())
                    .build();
        }

        User user = userRepository.findById(userIdOpt.get());
        String notes =
                body != null && body.getNotes() != null && !body.getNotes().isBlank()
                        ? body.getNotes().trim()
                        : null;

        try {
            TripChecklistItem item =
                    TripChecklistItem.builder()
                            .trip(trip)
                            .title(title)
                            .notes(notes)
                            .completed(false)
                            .sortOrder(checklistRepository.nextSortOrder(tripId))
                            .createdBy(user)
                            .build();
            checklistRepository.persist(item);

            auditService.record(
                    trip, userIdOpt.get(),
                    B2bTripLogAction.CHECKLIST_ITEM_CREATED,
                    "CHECKLIST_ITEM", item.id,
                    "Item de checklist criado: '" + title + "'");

            return Response.status(Response.Status.CREATED).entity(toResponse(item)).build();
        } catch (Exception e) {
            log.error("Create checklist item failed tripId={}", tripId, e);
            return serverError("Failed to create checklist item");
        }
    }

    @PATCH
    @Path("/{tripId}/checklist/{itemId}")
    @Transactional
    @Operation(
        summary = "Atualizar item de checklist",
        description = "Atualiza parcialmente o título, as anotações ou o status de conclusão de um item de checklist."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Item atualizado com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem"),
        @APIResponse(responseCode = "404", description = "Item de checklist ou viagem não encontrados")
    })
    public Response updateItem(
            @PathParam("tripId") Long tripId,
            @PathParam("itemId") Long itemId,
            @RequestBody(description = "Dados para atualização do item", required = true) UpdateTripChecklistItemRequest body,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            return forbiddenResponse();
        }

        Optional<TripChecklistItem> itemOpt = checklistRepository.findByIdAndTripId(itemId, tripId);
        if (itemOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message("Checklist item not found").build())
                    .build();
        }

        TripChecklistItem item = itemOpt.get();

        // Snapshot antes da edição
        String prevTitle = item.getTitle();
        Boolean prevCompleted = item.getCompleted();

        if (body != null) {
            if (body.getTitle() != null) {
                String title = body.getTitle().trim();
                if (title.isEmpty()) {
                    return badRequest("INVALID_TITLE", "Title cannot be empty");
                }
                if (title.length() > 500) {
                    return badRequest("INVALID_TITLE", "Title must be at most 500 characters");
                }
                item.setTitle(title);
            }
            if (body.getNotes() != null) {
                String notes = body.getNotes().trim();
                item.setNotes(notes.isEmpty() ? null : notes);
            }
            if (body.getCompleted() != null) {
                item.setCompleted(body.getCompleted());
            }
        }

        try {
            checklistRepository.persist(item);

            Trip trip = tripRepository.findById(tripId);
            final Long actorId = userIdOpt.get();
            String prevJson = "{\"title\":\"" + esc(prevTitle) + "\",\"completed\":" + prevCompleted + "}";
            String newJson  = "{\"title\":\"" + esc(item.getTitle()) + "\",\"completed\":" + item.getCompleted() + "}";
            auditService.record(
                    trip, actorId,
                    B2bTripLogAction.CHECKLIST_ITEM_UPDATED,
                    "CHECKLIST_ITEM", itemId,
                    prevJson, newJson,
                    "Item de checklist atualizado: '" + item.getTitle() + "'",
                    null);

            return Response.ok(toResponse(item)).build();
        } catch (Exception e) {
            log.error("Update checklist item failed tripId={} itemId={}", tripId, itemId, e);
            return serverError("Failed to update checklist item");
        }
    }

    @DELETE
    @Path("/{tripId}/checklist/{itemId}")
    @Transactional
    @Operation(
        summary = "Excluir item de checklist",
        description = "Remove definitivamente um item de checklist de uma viagem."
    )
    @APIResponses({
        @APIResponse(responseCode = "240", description = "Item excluído com sucesso (No Content)"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "403", description = "Acesso proibido a esta viagem"),
        @APIResponse(responseCode = "404", description = "Item de checklist ou viagem não encontrados")
    })
    public Response deleteItem(
            @PathParam("tripId") Long tripId,
            @PathParam("itemId") Long itemId,
            @Context HttpHeaders headers) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return unauthorizedResponse();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            return forbiddenResponse();
        }

        Optional<TripChecklistItem> itemOpt = checklistRepository.findByIdAndTripId(itemId, tripId);
        if (itemOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message("Checklist item not found").build())
                    .build();
        }

        TripChecklistItem item = itemOpt.get();
        String deletedTitle = item.getTitle();

        try {
            checklistRepository.delete(item);

            Trip trip = tripRepository.findById(tripId);
            auditService.record(
                    trip, userIdOpt.get(),
                    B2bTripLogAction.CHECKLIST_ITEM_DELETED,
                    "CHECKLIST_ITEM", itemId,
                    "Item de checklist excluído: '" + deletedTitle + "'");

            return Response.noContent().build();
        } catch (Exception e) {
            log.error("Delete checklist item failed tripId={} itemId={}", tripId, itemId, e);
            return serverError("Failed to delete checklist item");
        }
    }

    private TripChecklistItemResponse toResponse(TripChecklistItem item) {
        return TripChecklistItemResponse.builder()
                .id(item.id)
                .tripId(item.getTrip().id)
                .title(item.getTitle())
                .notes(item.getNotes())
                .completed(Boolean.TRUE.equals(item.getCompleted()))
                .sortOrder(item.getSortOrder())
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null)
                .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null)
                .build();
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
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Checklist auth failed: {}", e.getMessage());
            return Optional.empty();
        }
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

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
