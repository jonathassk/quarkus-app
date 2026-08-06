package org.example.domain.enums;

public enum OpportunityTaskStatus {
    OPEN,
    DONE;

    public static OpportunityTaskStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPEN;
        }
        return OpportunityTaskStatus.valueOf(raw.trim().toUpperCase());
    }
}
