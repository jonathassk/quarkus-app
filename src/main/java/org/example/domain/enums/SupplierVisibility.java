package org.example.domain.enums;

public enum SupplierVisibility {
    SHOW_NAME,
    DESCRIPTION_ONLY,
    HIDE_UNTIL_APPROVAL;

    public static SupplierVisibility fromString(String value) {
        if (value == null || value.isBlank()) {
            return SHOW_NAME;
        }
        for (SupplierVisibility v : values()) {
            if (v.name().equalsIgnoreCase(value.trim())) {
                return v;
            }
        }
        return SHOW_NAME;
    }
}
