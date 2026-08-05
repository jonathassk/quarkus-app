package org.example.domain.enums;

public enum OpportunityPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    public static OpportunityPriority fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        return OpportunityPriority.valueOf(raw.trim().toUpperCase());
    }
}
