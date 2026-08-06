package org.example.domain.enums;

/** Estado de documento operacional (voucher, bilhete, apólice…). */
public enum OperationalDocumentStatus {
    NOT_RECEIVED,
    RECEIVED,
    IN_REVIEW,
    APPROVED,
    REPLACED,
    SENT_TO_CLIENT;

    public static OperationalDocumentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return NOT_RECEIVED;
        }
        for (OperationalDocumentStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return NOT_RECEIVED;
    }
}
