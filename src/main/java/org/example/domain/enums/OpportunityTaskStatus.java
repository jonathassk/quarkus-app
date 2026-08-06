package org.example.domain.enums;

public enum OpportunityTaskStatus {
    OPEN,
    WAITING,
    DONE,
    CANCELLED;

    public static OpportunityTaskStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPEN;
        }
        return OpportunityTaskStatus.valueOf(raw.trim().toUpperCase());
    }

    public boolean isOpenLike() {
        return this == OPEN || this == WAITING;
    }
}
