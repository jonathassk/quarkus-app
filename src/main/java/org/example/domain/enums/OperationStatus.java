package org.example.domain.enums;

/**
 * Status geral da viagem no workspace operacional (calculado a partir dos serviços).
 */
public enum OperationStatus {
    /** Preparando reservas — ainda não iniciou solicitações. */
    PREPARING_RESERVATIONS,
    /** Reservas em andamento. */
    RESERVATIONS_IN_PROGRESS,
    /** Parcialmente confirmada. */
    PARTIALLY_CONFIRMED,
    /** Pronta para viajar. */
    READY_TO_TRAVEL,
    /** Em viagem. */
    IN_TRIP,
    /** Concluída. */
    COMPLETED,
    /** Cancelada. */
    CANCELLED;

    public static OperationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return PREPARING_RESERVATIONS;
        }
        String v = value.trim();
        // Legado (pré-V36)
        return switch (v.toUpperCase()) {
            case "TO_RESERVE" -> PREPARING_RESERVATIONS;
            case "REQUESTED" -> RESERVATIONS_IN_PROGRESS;
            case "RESERVED" -> PARTIALLY_CONFIRMED;
            case "ISSUED" -> READY_TO_TRAVEL;
            default -> {
                for (OperationStatus s : values()) {
                    if (s.name().equalsIgnoreCase(v)) {
                        yield s;
                    }
                }
                throw new IllegalArgumentException("operation_status inválido: " + value);
            }
        };
    }
}
