package org.example.domain.enums;

public enum OpportunityFileKind {
    QUOTE,
    IMAGE,
    PDF,
    OTHER;

    public static OpportunityFileKind fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        try {
            return OpportunityFileKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    public static OpportunityFileKind fromContentType(String contentType, String fileName) {
        String ct = contentType != null ? contentType.toLowerCase() : "";
        String name = fileName != null ? fileName.toLowerCase() : "";
        if (ct.startsWith("image/") || name.matches(".*\\.(png|jpe?g|gif|webp)$")) {
            return IMAGE;
        }
        if (ct.contains("pdf") || name.endsWith(".pdf")) {
            return PDF;
        }
        if (name.contains("cotac") || name.contains("quote") || name.contains("orcamento")) {
            return QUOTE;
        }
        return OTHER;
    }
}
