package org.example.domain.enums;

public enum ProposalOptionPosition {
    ESSENTIAL,
    RECOMMENDED,
    PREMIUM,
    CUSTOM;

    public static ProposalOptionPosition fromString(String value) {
        if (value == null || value.isBlank()) {
            return RECOMMENDED;
        }
        for (ProposalOptionPosition p : values()) {
            if (p.name().equalsIgnoreCase(value.trim())) {
                return p;
            }
        }
        return CUSTOM;
    }
}
