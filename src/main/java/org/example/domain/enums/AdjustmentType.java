package org.example.domain.enums;

public enum AdjustmentType {
    DISCOUNT_PERCENT,
    DISCOUNT_FIXED,
    COURTESY,
    EXTRA_FEE,
    ROUNDING,
    COMMERCIAL;

    public static AdjustmentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return COMMERCIAL;
        }
        for (AdjustmentType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return COMMERCIAL;
    }
}
