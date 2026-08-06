package org.example.domain.enums;

public enum OpportunityTaskPriority {
    NORMAL,
    IMPORTANT,
    CRITICAL;

    public static OpportunityTaskPriority fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        return OpportunityTaskPriority.valueOf(raw.trim().toUpperCase());
    }
}
