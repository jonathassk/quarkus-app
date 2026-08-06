package org.example.domain.enums;

public enum OpportunityTaskOrigin {
    MANUAL,
    AUTOMATION;

    public static OpportunityTaskOrigin fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        return OpportunityTaskOrigin.valueOf(raw.trim().toUpperCase());
    }
}
