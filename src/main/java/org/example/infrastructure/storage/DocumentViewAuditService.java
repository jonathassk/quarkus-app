package org.example.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Trip;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Log de visualização de documentos pessoais no R2 (barato, acesso raro).
 *
 * <p>Cada abertura gera um JSON ~200 B em
 * {@code audit/document-views/exp/{yyyy}/{MM}/{dd}/{uuid}.json}, onde a data do
 * prefixo é {@code retain_until = fim_da_viagem + 3 meses} (ou hoje + 3 meses se
 * a viagem não tiver {@code end_date}).
 *
 * <p>Purge: {@link #purgeExpired(LocalDate)} lista o prefixo e apaga chaves cujo
 * dia no path é &lt; cutoff.
 */
@Slf4j
@ApplicationScoped
public class DocumentViewAuditService {

    private static final String PREFIX = "audit/document-views/exp/";
    private static final int RETENTION_MONTHS = 3;

    @Inject
    ObjectStorageService objectStorageService;

    @Inject
    ObjectMapper objectMapper;

    public void recordView(
            Trip trip,
            UUID documentId,
            UUID viewerUserId,
            String documentTitle,
            String ipAddress) {
        if (!objectStorageService.isConfigured() || trip == null || documentId == null || viewerUserId == null) {
            return;
        }
        try {
            LocalDate retainUntil = resolveRetainUntil(trip);
            String key = PREFIX
                    + retainUntil.getYear() + "/"
                    + String.format("%02d", retainUntil.getMonthValue()) + "/"
                    + String.format("%02d", retainUntil.getDayOfMonth()) + "/"
                    + UUID.randomUUID() + ".json";

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tripId", trip.id != null ? trip.id.toString() : null);
            body.put("documentId", documentId.toString());
            body.put("viewerUserId", viewerUserId.toString());
            body.put("viewedAt", Instant.now().toString());
            body.put("retainUntil", retainUntil.toString());
            if (documentTitle != null && !documentTitle.isBlank()) {
                body.put("title", truncate(documentTitle, 255));
            }
            if (ipAddress != null && !ipAddress.isBlank()) {
                body.put("ip", truncate(ipAddress, 45));
            }

            byte[] json = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            objectStorageService.putObject(key, json, "application/json");
        } catch (Exception e) {
            log.error(
                    "Document view audit failed (non-blocking) tripId={} documentId={} viewer={}",
                    trip.id,
                    documentId,
                    viewerUserId,
                    e);
        }
    }

    /**
     * Remove logs cujo {@code retain_until} (data no path) é anterior a {@code beforeExclusive}.
     *
     * @return quantidade de objetos apagados
     */
    public int purgeExpired(LocalDate beforeExclusive) {
        if (!objectStorageService.isConfigured()) {
            return 0;
        }
        LocalDate cutoff = beforeExclusive != null ? beforeExclusive : LocalDate.now(ZoneOffset.UTC);
        int deleted = 0;
        String continuation = null;
        do {
            ListObjectsV2Response page = objectStorageService.listObjects(PREFIX, continuation);
            if (page == null || page.contents() == null || page.contents().isEmpty()) {
                break;
            }
            for (S3Object obj : page.contents()) {
                LocalDate retainDay = parseRetainDayFromKey(obj.key());
                if (retainDay == null || !retainDay.isBefore(cutoff)) {
                    continue;
                }
                try {
                    objectStorageService.deleteObject(obj.key());
                    deleted++;
                } catch (Exception e) {
                    log.warn("Failed to delete audit object key={}", obj.key(), e);
                }
            }
            continuation = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (continuation != null);

        if (deleted > 0) {
            log.info("Purged {} document view audit object(s) before {}", deleted, cutoff);
        }
        return deleted;
    }

    static LocalDate resolveRetainUntil(Trip trip) {
        LocalDate end = trip.getEndDate() != null ? trip.getEndDate() : LocalDate.now(ZoneOffset.UTC);
        return end.plusMonths(RETENTION_MONTHS);
    }

    /**
     * Key shape: {@code audit/document-views/exp/yyyy/MM/dd/uuid.json}
     */
    static LocalDate parseRetainDayFromKey(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        String rest = key.substring(PREFIX.length());
        String[] parts = rest.split("/");
        if (parts.length < 3) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
