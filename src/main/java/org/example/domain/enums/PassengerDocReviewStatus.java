package org.example.domain.enums;

public enum PassengerDocReviewStatus {
    NOT_PROVIDED,
    UPLOADED,
    IN_REVIEW,
    VALID,
    EXPIRING,
    EXPIRED,
    REJECTED;

    public static PassengerDocReviewStatus fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NOT_PROVIDED;
        }
        try {
            return PassengerDocReviewStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NOT_PROVIDED;
        }
    }
}
