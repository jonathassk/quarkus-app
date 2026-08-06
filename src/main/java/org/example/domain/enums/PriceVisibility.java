package org.example.domain.enums;

public enum PriceVisibility {
    TOTAL_ONLY,
    BY_CATEGORY,
    PER_ITEM,
    PER_PERSON,
    TOTAL_AND_PER_PERSON;

    public static PriceVisibility fromString(String value) {
        if (value == null || value.isBlank()) {
            return TOTAL_ONLY;
        }
        for (PriceVisibility v : values()) {
            if (v.name().equalsIgnoreCase(value.trim())) {
                return v;
            }
        }
        return TOTAL_ONLY;
    }
}
