package org.example.domain.enums;

public enum OpportunityTaskCompletionOutcome {
    CLIENT_REPLIED,
    NO_REPLY,
    INFO_RECEIVED,
    PROPOSAL_SENT,
    PAYMENT_CONFIRMED,
    SUPPLIER_REPLIED,
    RESCHEDULED,
    OTHER;

    public static OpportunityTaskCompletionOutcome fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OpportunityTaskCompletionOutcome.valueOf(raw.trim().toUpperCase());
    }
}
