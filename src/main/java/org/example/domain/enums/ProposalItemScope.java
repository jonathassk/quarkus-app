package org.example.domain.enums;

public enum ProposalItemScope {
    COMMON,
    OPTION;

    public static ProposalItemScope fromString(String value) {
        if (value == null || value.isBlank()) {
            return OPTION;
        }
        for (ProposalItemScope s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("item scope inválido: " + value);
    }
}
