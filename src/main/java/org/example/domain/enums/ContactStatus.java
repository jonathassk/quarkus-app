package org.example.domain.enums;

/**
 * Estado do contato CRM (sem entidade Lead separada).
 */
public enum ContactStatus {
    PROSPECT,
    CLIENT,
    INACTIVE;

    public static ContactStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return PROSPECT;
        }
        return ContactStatus.valueOf(raw.trim().toUpperCase());
    }
}
