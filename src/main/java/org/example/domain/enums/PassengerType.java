package org.example.domain.enums;

public enum PassengerType {
    ADULT,
    CHILD,
    INFANT;

    public static PassengerType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ADULT;
        }
        try {
            return PassengerType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ADULT;
        }
    }
}
