package org.example.domain.enums;

/** Ciclo de alteração pós-aprovação. */
public enum ServiceChangeStatus {
    REQUESTED,
    IN_ANALYSIS,
    QUOTED,
    AWAITING_CLIENT,
    APPROVED,
    EXECUTED,
    REFUSED,
    CANCELLED;

    public static ServiceChangeStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return REQUESTED;
        }
        for (ServiceChangeStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("service_change_status inválido: " + value);
    }
}
