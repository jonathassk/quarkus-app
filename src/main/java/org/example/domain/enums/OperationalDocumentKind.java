package org.example.domain.enums;

/** Tipo de documento operacional ou de identidade do passageiro. */
public enum OperationalDocumentKind {
    VOUCHER,
    TICKET,
    HOTEL_CONFIRMATION,
    POLICY,
    TICKET_ENTRY,
    RENTAL_CONTRACT,
    SUPPLIER_PROOF,
    PASSPORT,
    RG,
    CNH,
    OTHER;

    public static OperationalDocumentKind fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (OperationalDocumentKind k : values()) {
            if (k.name().equalsIgnoreCase(value.trim())) {
                return k;
            }
        }
        return OTHER;
    }
}
