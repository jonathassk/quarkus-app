package org.example.domain.enums;

/** Tipo de serviço operacional (espelha ProposalItemType, sem PACKAGE). */
public enum OperationalServiceType {
    FLIGHT,
    HOTEL,
    TRANSFER,
    ACTIVITY,
    INSURANCE,
    CAR_RENTAL,
    OTHER;

    public static OperationalServiceType fromProposalItemType(ProposalItemType type) {
        if (type == null) {
            return OTHER;
        }
        return switch (type) {
            case FLIGHT -> FLIGHT;
            case HOTEL -> HOTEL;
            case TRANSFER -> TRANSFER;
            case ACTIVITY -> ACTIVITY;
            case INSURANCE -> INSURANCE;
            case PACKAGE, OTHER -> OTHER;
        };
    }

    public static OperationalServiceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        for (OperationalServiceType t : values()) {
            if (t.name().equalsIgnoreCase(value.trim())) {
                return t;
            }
        }
        return OTHER;
    }

    /** "Emitido" faz sentido para passagem e seguro. */
    public boolean supportsIssuedStatus() {
        return this == FLIGHT || this == INSURANCE;
    }

    public String defaultNextAction() {
        return switch (this) {
            case FLIGHT -> "CHECK_AVAILABILITY";
            case HOTEL -> "CHECK_AVAILABILITY";
            case TRANSFER -> "CONFIRM_FLIGHT_TIME";
            case ACTIVITY -> "REQUEST_PARTICIPANT_LIST";
            case INSURANCE -> "REQUEST_DOCS";
            case CAR_RENTAL -> "CHECK_AVAILABILITY";
            case OTHER -> "CHECK_AVAILABILITY";
        };
    }

    public String defaultNextActionLabel() {
        return switch (this) {
            case FLIGHT -> "Consultar disponibilidade";
            case HOTEL -> "Confirmar disponibilidade";
            case TRANSFER -> "Conferir horário do voo";
            case ACTIVITY -> "Enviar lista de participantes";
            case INSURANCE -> "Solicitar documentos";
            case CAR_RENTAL -> "Consultar disponibilidade";
            case OTHER -> "Consultar disponibilidade";
        };
    }
}
