package org.example.domain.enums;

/**
 * Tipos de notificação in-app (e opcionalmente e-mail de atividade).
 */
public enum NotificationKind {
    TRIP_SHARED,
    TRIP_COMMENT,
    CHAT_MESSAGE,
    EVENT_RSVP,
    DOC_EXPIRING,
    PROPOSAL_SENT,
    PROPOSAL_APPROVED,
    PAYMENT_CONFIRMED;

    public static NotificationKind fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("notification kind is required");
        }
        for (NotificationKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        throw new IllegalArgumentException("notification kind inválido: " + value);
    }
}
