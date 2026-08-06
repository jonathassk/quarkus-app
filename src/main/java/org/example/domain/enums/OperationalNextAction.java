package org.example.domain.enums;

/**
 * Próxima ação operacional sugerida por serviço (MVP §8.7).
 */
public enum OperationalNextAction {
    CHECK_AVAILABILITY,
    CONFIRM_AVAILABILITY,
    SEND_GUEST_NAMES,
    CONFIRM_RESERVATION,
    EMIT,
    REQUEST_VOUCHER,
    CONFIRM_FLIGHT_TIME,
    INFORM_SUPPLIER,
    COLLECT_PARTICIPANTS,
    COLLECT_DOCUMENTS,
    REQUEST_RESERVATION,
    REQUEST_CORRECTION,
    CANCEL,
    NONE;

    public static OperationalNextAction fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        for (OperationalNextAction a : values()) {
            if (a.name().equalsIgnoreCase(value.trim())) {
                return a;
            }
        }
        return NONE;
    }

    public String defaultLabelPt() {
        return switch (this) {
            case CHECK_AVAILABILITY -> "Consultar disponibilidade";
            case CONFIRM_AVAILABILITY -> "Confirmar disponibilidade";
            case SEND_GUEST_NAMES -> "Enviar nomes dos hóspedes";
            case CONFIRM_RESERVATION -> "Confirmar reserva";
            case EMIT -> "Emitir";
            case REQUEST_VOUCHER -> "Solicitar voucher";
            case CONFIRM_FLIGHT_TIME -> "Conferir horário do voo";
            case INFORM_SUPPLIER -> "Informar fornecedor";
            case COLLECT_PARTICIPANTS -> "Coletar lista de participantes";
            case COLLECT_DOCUMENTS -> "Coletar documentos";
            case REQUEST_RESERVATION -> "Solicitar reserva";
            case REQUEST_CORRECTION -> "Solicitar correção";
            case CANCEL -> "Cancelar";
            case NONE -> "—";
        };
    }
}
