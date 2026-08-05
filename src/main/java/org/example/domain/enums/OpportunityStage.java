package org.example.domain.enums;

/**
 * Etapas da solicitação/oportunidade comercial.
 */
public enum OpportunityStage {
    NEW,
    QUALIFYING,
    QUOTING,
    NEGOTIATING,
    WON,
    LOST;

    public static OpportunityStage fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEW;
        }
        return OpportunityStage.valueOf(raw.trim().toUpperCase());
    }

    public boolean isTerminal() {
        return this == WON || this == LOST;
    }
}
