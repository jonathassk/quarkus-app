package org.example.domain.enums;

public enum QualificationStatus {
    INSUFFICIENT,
    PARTIAL,
    READY_TO_QUOTE;

    public static QualificationStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return INSUFFICIENT;
        }
        return QualificationStatus.valueOf(raw.trim().toUpperCase());
    }
}
