package org.example.domain.enums;

/**
 * Status da versão comercial da proposta (independente do kanban do Trip).
 */
public enum CommercialProposalStatus {
    DRAFT,
    SENT,
    VIEWED,
    CHANGE_REQUESTED,
    APPROVED,
    REJECTED,
    EXPIRED,
    SUPERSEDED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == EXPIRED || this == SUPERSEDED;
    }

    public static CommercialProposalStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        for (CommercialProposalStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("commercial proposal status inválido: " + value);
    }
}
