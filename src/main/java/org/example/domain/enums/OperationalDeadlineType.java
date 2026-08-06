package org.example.domain.enums;

/**
 * Tipos de prazo operacional (MVP §8.8).
 */
public enum OperationalDeadlineType {
    QUOTE_VALIDITY,
    PRE_RESERVATION_LIMIT,
    ISSUANCE_DEADLINE,
    FREE_CANCELLATION,
    PASSENGER_LIST,
    CHECK_IN,
    SUPPLIER_DEADLINE,
    VOUCHER_DELIVERY,
    OTHER;

    public static OperationalDeadlineType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (OperationalDeadlineType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return OTHER;
    }
}
