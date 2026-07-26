package org.example.domain.enums;

/**
 * Status de um {@link org.example.domain.entity.TripPayment}.
 */
public enum TripPaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED;

    public static TripPaymentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        for (TripPaymentStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("payment status inválido: " + value);
    }
}
