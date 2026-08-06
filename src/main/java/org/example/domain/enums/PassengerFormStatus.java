package org.example.domain.enums;

public enum PassengerFormStatus {
    NOT_REQUESTED,
    INVITED,
    IN_PROGRESS,
    SUBMITTED,
    IN_REVIEW,
    CORRECTION_REQUESTED,
    COMPLETE;

    public static PassengerFormStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NOT_REQUESTED;
        }
        try {
            return PassengerFormStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NOT_REQUESTED;
        }
    }
}
