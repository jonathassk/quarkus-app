package org.example.domain.enums;

public enum OpportunityNextActionType {
    CALL,
    WHATSAPP,
    EMAIL,
    REQUEST_INFO,
    CHECK_SUPPLIER,
    PREPARE_PROPOSAL,
    FOLLOW_UP,
    CONFIRM_PAYMENT,
    OTHER;

    public static OpportunityNextActionType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OpportunityNextActionType.valueOf(raw.trim().toUpperCase());
    }
}
