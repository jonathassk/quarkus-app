package org.example.domain.enums;

/**
 * Status operacional por serviço (MVP §8.3).
 * {@link #ISSUED} só faz sentido para voo e seguro.
 */
public enum OperationalServiceStatus {
    TO_RESERVE,
    REQUESTED,
    WAITING,
    PRE_RESERVED,
    CONFIRMED,
    ISSUED,
    CHANGE_PENDING,
    CANCELLED;

    public static OperationalServiceStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return TO_RESERVE;
        }
        for (OperationalServiceStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("operational_service_status inválido: " + value);
    }

    public boolean isTerminalSuccess() {
        return this == CONFIRMED || this == ISSUED;
    }

    /** Alias de {@link #isTerminalSuccess()}. */
    public boolean isSettled() {
        return isTerminalSuccess();
    }

    public boolean isActive() {
        return this != CANCELLED;
    }
}
