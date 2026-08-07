package org.example.domain.enums;

public enum AddOptionFromTripMode {
    CLONE,
    LINK;

    public static AddOptionFromTripMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return CLONE;
        }
        return AddOptionFromTripMode.valueOf(value.trim().toUpperCase());
    }
}
