package org.example.domain.enums;

public enum PricingEditMode {
    QUICK,
    DETAILED;

    public static PricingEditMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return QUICK;
        }
        for (PricingEditMode m : values()) {
            if (m.name().equalsIgnoreCase(value.trim())) {
                return m;
            }
        }
        throw new IllegalArgumentException("pricing_edit_mode inválido: " + value);
    }
}
