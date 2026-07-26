package org.example.domain.enums;

/**
 * Tipo de cobrança ligada a uma proposta B2B.
 */
public enum TripPaymentKind {
    /** Sinal (percentual do valor final). */
    DEPOSIT,
    /** Saldo restante após o sinal. */
    BALANCE,
    /** Valor cheio em uma única cobrança. */
    FULL;

    public static TripPaymentKind fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("payment kind is required");
        }
        for (TripPaymentKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        throw new IllegalArgumentException("payment kind inválido: " + value);
    }
}
