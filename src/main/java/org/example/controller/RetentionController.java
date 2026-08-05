package org.example.controller;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.common.ApiErrorBody;
import org.example.infrastructure.storage.DocumentViewAuditService;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Endpoints internos de retenção (protegidos por {@code INTERNAL_SECRET}).
 * Pensados para EventBridge / cron barato — acesso humano mínimo.
 */
@Slf4j
@Tag(name = "Internal Retention", description = "Jobs de purge (segredo interno)")
@Path("/api/v1/internal/retention")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class RetentionController {

    private final DocumentViewAuditService documentViewAuditService;

    @ConfigProperty(name = "internal.secret", defaultValue = "")
    String internalSecret;

    @POST
    @Path("/purge-document-view-audits")
    @Operation(summary = "Apaga logs de visualização de documentos com retain_until &lt; hoje (UTC)")
    public Response purgeDocumentViewAudits(@Context HttpHeaders headers) {
        if (!isAuthorized(headers)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiErrorBody.builder()
                            .code("UNAUTHORIZED")
                            .message("Invalid internal secret")
                            .build())
                    .build();
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int deleted = documentViewAuditService.purgeExpired(today);
        return Response.ok(Map.of(
                "deleted", deleted,
                "before", today.toString()
        )).build();
    }

    private boolean isAuthorized(HttpHeaders headers) {
        if (internalSecret == null || internalSecret.isBlank() || internalSecret.length() < 32) {
            log.warn("INTERNAL_SECRET missing/weak — rejecting retention purge");
            return false;
        }
        String provided = headers != null ? headers.getHeaderString("X-Internal-Secret") : null;
        if (provided == null || provided.isBlank()) {
            String auth = headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null;
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                provided = auth.substring(7).trim();
            }
        }
        return internalSecret.equals(provided);
    }
}
