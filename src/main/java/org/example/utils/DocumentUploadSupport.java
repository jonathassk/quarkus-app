package org.example.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validação e normalização de uploads de documentos (presigned R2).
 */
public final class DocumentUploadSupport {

    /** 10 MiB — must match frontend validation */
    public static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final Map<String, String> EXTENSION_TO_MIME = Map.ofEntries(
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    );

    private DocumentUploadSupport() {
    }

    public record ResolvedUpload(String fileName, String contentType) {
    }

    public static Optional<ResolvedUpload> resolve(String fileName, String rawContentType) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }

        String sanitizedName = sanitizeFileName(fileName.trim());
        if (sanitizedName.isBlank()) {
            return Optional.empty();
        }

        String normalizedType = normalizeContentType(rawContentType);
        if (normalizedType == null || normalizedType.isBlank()
                || "application/octet-stream".equals(normalizedType)) {
            normalizedType = mimeFromFileName(sanitizedName).orElse(null);
        }

        if (normalizedType == null || !ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedUpload(sanitizedName, normalizedType));
    }

    public static String unsupportedTypeMessage(String rawContentType, String fileName) {
        String normalized = normalizeContentType(rawContentType);
        if (normalized == null || normalized.isBlank()) {
            normalized = "(empty)";
        }
        return "Unsupported content type: " + normalized
                + ". Allowed: PDF, JPEG, PNG, WebP, GIF, DOC, DOCX. fileName="
                + fileName;
    }

    private static String sanitizeFileName(String fileName) {
        String base = fileName.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        if (cleaned.isEmpty()) {
            return "document";
        }
        if (cleaned.length() > 200) {
            int dot = cleaned.lastIndexOf('.');
            if (dot > 0 && dot < cleaned.length() - 1) {
                String ext = cleaned.substring(dot);
                String stem = cleaned.substring(0, dot);
                int maxStem = Math.max(1, 200 - ext.length());
                cleaned = stem.substring(0, Math.min(stem.length(), maxStem)) + ext;
            } else {
                cleaned = cleaned.substring(0, 200);
            }
        }
        return cleaned;
    }

    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String type = raw.trim().toLowerCase(Locale.ROOT);
        int semi = type.indexOf(';');
        if (semi > 0) {
            type = type.substring(0, semi).trim();
        }
        return type;
    }

    private static Optional<String> mimeFromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(EXTENSION_TO_MIME.get(fileName.substring(dot).toLowerCase(Locale.ROOT)));
    }

    public static String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 10) {
            return "";
        }
        return ext.replaceAll("[^a-z0-9.]", "");
    }
}
