package org.example.domain.enums;

public enum OpportunityTaskWaitingOn {
    CLIENT,
    SUPPLIER,
    CONSULTANT,
    FINANCE,
    PARTNER,
    OTHER;

    public static OpportunityTaskWaitingOn fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OpportunityTaskWaitingOn.valueOf(raw.trim().toUpperCase());
    }
}
