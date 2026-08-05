package org.example.domain.enums;

/**
 * Subestado operacional da viagem após a venda (badge no card Confirmada / Em viagem).
 */
public enum OperationStatus {
    /** Aguardando iniciar reservas. */
    TO_RESERVE,
    /** Reserva solicitada ao fornecedor. */
    REQUESTED,
    /** Reserva confirmada. */
    RESERVED,
    /** Documentos / bilhetes emitidos. */
    ISSUED,
    /** Operação cancelada. */
    CANCELLED;

    public static OperationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return TO_RESERVE;
        }
        for (OperationStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("operation_status inválido: " + value);
    }
}
