package org.example.domain.enums;

public enum MarkupKind {
    PERCENT,
    FIXED;

    public static MarkupKind fromString(String value) {
        if (value == null || value.isBlank()) {
            return PERCENT;
        }
        for (MarkupKind k : values()) {
            if (k.name().equalsIgnoreCase(value.trim())) {
                return k;
            }
        }
        return PERCENT;
    }
}
