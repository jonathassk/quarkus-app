package org.example.domain.enums;

public enum OpportunityNextActionType {
    CALL,
    WHATSAPP,
    EMAIL,
    FIRST_CONTACT,
    REQUEST_INFO,
    CHECK_SUPPLIER,
    PREPARE_PROPOSAL,
    SEND_PROPOSAL,
    FOLLOW_UP,
    CONFIRM_PAYMENT,
    REQUEST_DEPOSIT,
    RESERVE_SERVICE,
    REQUEST_DOCS,
    PASSENGER_FORM_PENDING,
    PASSENGER_DOC_EXPIRING,
    PASSENGER_DOC_EXPIRED,
    PASSENGER_NO_RESPONSE,
    SEND_VOUCHERS,
    REVIEW_CHANGE,
    OTHER;

    public static OpportunityNextActionType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OpportunityNextActionType.valueOf(raw.trim().toUpperCase());
    }

    public String defaultTitle() {
        return switch (this) {
            case CALL -> "Entrar em contato";
            case WHATSAPP -> "Enviar WhatsApp";
            case EMAIL -> "Enviar e-mail";
            case FIRST_CONTACT -> "Primeiro contato";
            case REQUEST_INFO -> "Solicitar informações";
            case CHECK_SUPPLIER -> "Consultar fornecedor";
            case PREPARE_PROPOSAL -> "Preparar proposta";
            case SEND_PROPOSAL -> "Enviar proposta";
            case FOLLOW_UP -> "Fazer follow-up";
            case CONFIRM_PAYMENT -> "Confirmar pagamento";
            case REQUEST_DEPOSIT -> "Solicitar entrada";
            case RESERVE_SERVICE -> "Reservar serviço";
            case REQUEST_DOCS -> "Solicitar documentos";
            case PASSENGER_FORM_PENDING -> "Formulário de passageiro pendente";
            case PASSENGER_DOC_EXPIRING -> "Documento próximo do vencimento";
            case PASSENGER_DOC_EXPIRED -> "Documento vencido";
            case PASSENGER_NO_RESPONSE -> "Passageiro não respondeu";
            case SEND_VOUCHERS -> "Enviar vouchers";
            case REVIEW_CHANGE -> "Revisar alteração solicitada";
            case OTHER -> "Outra ação";
        };
    }

    public OpportunityTaskType defaultTaskType() {
        return switch (this) {
            case CONFIRM_PAYMENT, REQUEST_DEPOSIT -> OpportunityTaskType.FINANCIAL;
            case RESERVE_SERVICE, SEND_VOUCHERS -> OpportunityTaskType.OPERATIONAL;
            case REQUEST_DOCS, PASSENGER_FORM_PENDING, PASSENGER_DOC_EXPIRING,
                    PASSENGER_DOC_EXPIRED, PASSENGER_NO_RESPONSE -> OpportunityTaskType.PASSENGER;
            default -> OpportunityTaskType.COMMERCIAL;
        };
    }
}
