package org.example.domain.enums;

public enum OpportunityLostReasonCode {
    PRICE,
    CLIENT_CANCELLED,
    DATES_CHANGED,
    OTHER_AGENCY,
    BOUGHT_DIRECT,
    NO_RESPONSE,
    DOCUMENTATION,
    NO_AVAILABILITY,
    PAYMENT_TERMS,
    DESTINATION_CHANGED,
    POSTPONED,
    BAD_FIT,
    DUPLICATE,
    OTHER;

    public static OpportunityLostReasonCode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OpportunityLostReasonCode.valueOf(raw.trim().toUpperCase());
    }
}
