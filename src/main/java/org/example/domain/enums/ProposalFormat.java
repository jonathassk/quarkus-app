package org.example.domain.enums;

public enum ProposalFormat {
    SINGLE,
    COMPARE;

    public static ProposalFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE;
        }
        for (ProposalFormat f : values()) {
            if (f.name().equalsIgnoreCase(value.trim())) {
                return f;
            }
        }
        return SINGLE;
    }
}
