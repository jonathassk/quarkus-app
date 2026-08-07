package org.example.domain.enums;

public enum TripPickerOrigin {
    ALL,
    AGENT,
    TRAVELER;

    public static TripPickerOrigin fromString(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return TripPickerOrigin.valueOf(value.trim().toUpperCase());
    }
}
