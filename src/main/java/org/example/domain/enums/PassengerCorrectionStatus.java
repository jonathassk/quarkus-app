package org.example.domain.enums;

public enum PassengerCorrectionStatus {
    OPEN,
    RESOLVED,
    CANCELLED;

    public static PassengerCorrectionStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPEN;
        }
        try {
            return PassengerCorrectionStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
