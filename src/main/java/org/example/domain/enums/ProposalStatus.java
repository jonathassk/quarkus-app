package org.example.domain.enums;

/**
 * Status do funil de proposta B2B (independente do {@link TripStatus} calendário).
 */
public enum ProposalStatus {
    DRAFT,
    SENT,
    APPROVED,
    REJECTED,
    LOST;

    public static ProposalStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        for (ProposalStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("proposal_status inválido: " + value);
    }
}
