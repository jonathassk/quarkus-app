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
import org.example.application.dto.documentexpiry.UpdateDocumentExpiryRequest;
import org.example.application.dto.documentexpiry.UpsertDocumentExpiryRequest;
import org.example.application.services.TokenService;
import org.example.application.services.documentexpiry.DocumentExpiryService;
import org.example.domain.enums.DocumentExpiryKind;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Documentos com data de validade acompanhados nas configurações do usuário
 * (passaporte, visto, CNH internacional e documentos extras cadastrados
 * livremente), usados para disparar alertas de expiração por e-mail.
 */
@Slf4j
@Tag(name = "Document Expiry", description = "Documentos com data de validade e alertas de expiração")
@Path("/api/v1/users/me/documents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class DocumentExpiryController {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final DocumentExpiryService documentExpiryService;

    @GET
    @Transactional
    @Operation(summary = "Listar documentos com data de validade do usuário")
    public Response list(@Context HttpHeaders headers) {
        return withAuth(headers, userId -> Response.ok(documentExpiryService.list(userId)).build());
    }

    @POST
    @Transactional
    @Operation(
            summary = "Criar/atualizar documento com data de validade",
            description = "Para kind fixo (PASSPORT, VISA, INTERNATIONAL_LICENSE) atualiza o registro existente "
                    + "do usuário (upsert). Para CUSTOM (padrão), sempre cria um novo documento — 'name' é obrigatório.")
    public Response upsert(UpsertDocumentExpiryRequest body, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> {
            if (body == null) {
                return badRequest("VALIDATION_ERROR", "Request body is required");
            }

            DocumentExpiryKind kind;
            try {
                kind = parseKind(body.getKind());
            } catch (IllegalArgumentException e) {
                return badRequest("VALIDATION_ERROR", "Invalid kind: " + body.getKind());
            }

            String name = body.getName() != null ? body.getName().trim() : null;
            if (kind == DocumentExpiryKind.CUSTOM && (name == null || name.isBlank())) {
                return badRequest("VALIDATION_ERROR", "name is required for custom documents");
            }
            if (name != null && name.length() > 255) {
                name = name.substring(0, 255);
            }

            LocalDate expiryDate;
            try {
                expiryDate = parseDate(body.getExpiryDate());
            } catch (DateTimeParseException e) {
                return badRequest("VALIDATION_ERROR", "expiryDate must be an ISO-8601 date (yyyy-MM-dd)");
            }

            try {
                var dto = documentExpiryService.upsert(userId, kind, name, expiryDate, body.getAlertEnabled());
                return Response.status(Response.Status.CREATED).entity(dto).build();
            } catch (Exception e) {
                log.error("Create document expiry failed userId={}", userId, e);
                return serverError("Failed to save document");
            }
        });
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Atualizar parcialmente um documento com data de validade")
    public Response update(
            @PathParam("id") UUID id, UpdateDocumentExpiryRequest body, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> {
            if (body == null) {
                return badRequest("VALIDATION_ERROR", "Request body is required");
            }

            LocalDate expiryDate = null;
            boolean expiryDateProvided = body.getExpiryDate() != null;
            if (expiryDateProvided) {
                try {
                    expiryDate = parseDate(body.getExpiryDate());
                } catch (DateTimeParseException e) {
                    return badRequest("VALIDATION_ERROR", "expiryDate must be an ISO-8601 date (yyyy-MM-dd)");
                }
            }

            String name = body.getName() != null ? body.getName().trim() : null;

            try {
                Optional<org.example.application.dto.documentexpiry.DocumentExpiryDTO> updated =
                        documentExpiryService.update(userId, id, name, expiryDate, expiryDateProvided, body.getAlertEnabled());
                if (updated.isEmpty()) {
                    return notFound();
                }
                return Response.ok(updated.get()).build();
            } catch (Exception e) {
                log.error("Update document expiry failed userId={} id={}", userId, id, e);
                return serverError("Failed to update document");
            }
        });
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Remover documento com data de validade")
    public Response delete(@PathParam("id") UUID id, @Context HttpHeaders headers) {
        return withAuth(headers, userId -> {
            boolean deleted = documentExpiryService.delete(userId, id);
            if (!deleted) {
                return notFound();
            }
            return Response.noContent().build();
        });
    }

    private static DocumentExpiryKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return DocumentExpiryKind.CUSTOM;
        }
        return DocumentExpiryKind.valueOf(raw.trim().toUpperCase());
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw.trim());
    }

    private Response withAuth(HttpHeaders headers, java.util.function.Function<UUID, Response> action) {
        Optional<UUID> userId = resolveAuthenticatedUserId(headers);
        if (userId.isEmpty()) {
            return unauthorizedResponse();
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
            log.warn("Document expiry auth failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorizedResponse() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiErrorBody.builder().code("UNAUTHORIZED").message("Invalid or expired token").build())
                .build();
    }

    private Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiErrorBody.builder().code("DOCUMENT_NOT_FOUND").message("Document not found").build())
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
}
