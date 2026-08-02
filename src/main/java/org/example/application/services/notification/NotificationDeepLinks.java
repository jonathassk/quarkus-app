package org.example.application.services.notification;

import org.example.domain.enums.NotificationKind;

import java.util.Locale;
import java.util.UUID;

/**
 * Deep links relativos alinhados às rotas do front (plan, event, chat, pipeline).
 */
public final class NotificationDeepLinks {

    private NotificationDeepLinks() {}

    public static String resolve(NotificationKind kind, String entityType, UUID entityId) {
        if (kind == null) {
            return "/";
        }

        // Kinds B2B têm preferência sobre entityType genérico (ex.: TRIP).
        if (kind == NotificationKind.PROPOSAL_SENT
                || kind == NotificationKind.PROPOSAL_APPROVED
                || kind == NotificationKind.PAYMENT_CONFIRMED) {
            return "/business/pipeline";
        }
        if (kind == NotificationKind.DOC_EXPIRING) {
            return "/settings";
        }
        if (kind == NotificationKind.CHAT_MESSAGE) {
            return entityId != null ? "/chat?conversationId=" + entityId : "/";
        }
        if (kind == NotificationKind.EVENT_RSVP) {
            return entityId != null ? "/event/" + entityId : "/events";
        }
        if (kind == NotificationKind.TRIP_INVITE) {
            return entityId != null ? "/plan/" + entityId : "/";
        }
        if (kind == NotificationKind.TRIP_SHARED || kind == NotificationKind.TRIP_COMMENT) {
            return entityId != null ? "/plan/" + entityId : "/";
        }

        String type = entityType != null ? entityType.trim().toUpperCase(Locale.ROOT) : "";
        if (entityId != null) {
            return switch (type) {
                case "TRIP" -> "/plan/" + entityId;
                case "CHAT", "CONVERSATION" -> "/chat?conversationId=" + entityId;
                case "EVENT" -> "/event/" + entityId;
                case "DOCUMENT" -> "/settings";
                case "PROPOSAL", "PAYMENT" -> "/business/pipeline";
                default -> "/";
            };
        }
        return "/";
    }
}
