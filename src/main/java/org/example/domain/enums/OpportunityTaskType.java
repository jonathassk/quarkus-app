package org.example.domain.enums;

public enum OpportunityTaskType {
    COMMERCIAL,
    FINANCIAL,
    OPERATIONAL,
    PASSENGER,
    PRE_POST_TRIP;

    public static OpportunityTaskType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMMERCIAL;
        }
        return OpportunityTaskType.valueOf(raw.trim().toUpperCase());
    }
}
