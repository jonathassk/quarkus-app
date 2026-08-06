package org.example.domain.enums;

public enum ProposalItemType {
    PACKAGE,
    FLIGHT,
    HOTEL,
    TRANSFER,
    ACTIVITY,
    INSURANCE,
    OTHER;

    public static ProposalItemType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (ProposalItemType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return OTHER;
    }
}
