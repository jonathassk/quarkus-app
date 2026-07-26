package org.example.application.services.notification;

import org.example.domain.enums.NotificationKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationDeepLinksTest {

    @Test
    void tripCommentLinksToPlan() {
        UUID tripId = UUID.randomUUID();
        assertEquals(
                "/plan/" + tripId,
                NotificationDeepLinks.resolve(NotificationKind.TRIP_COMMENT, "TRIP", tripId));
    }

    @Test
    void chatLinksToConversationQuery() {
        UUID conversationId = UUID.randomUUID();
        assertEquals(
                "/chat?conversationId=" + conversationId,
                NotificationDeepLinks.resolve(NotificationKind.CHAT_MESSAGE, "CONVERSATION", conversationId));
    }

    @Test
    void eventRsvpLinksToEventPage() {
        UUID eventId = UUID.randomUUID();
        assertEquals(
                "/event/" + eventId,
                NotificationDeepLinks.resolve(NotificationKind.EVENT_RSVP, "EVENT", eventId));
    }

    @Test
    void proposalAndPaymentLinkToPipeline() {
        UUID id = UUID.randomUUID();
        assertEquals(
                "/business/pipeline",
                NotificationDeepLinks.resolve(NotificationKind.PROPOSAL_APPROVED, "TRIP", id));
        assertEquals(
                "/business/pipeline",
                NotificationDeepLinks.resolve(NotificationKind.PAYMENT_CONFIRMED, "PAYMENT", id));
    }

    @Test
    void docExpiringLinksToSettings() {
        assertEquals(
                "/settings",
                NotificationDeepLinks.resolve(NotificationKind.DOC_EXPIRING, "DOCUMENT", UUID.randomUUID()));
    }
}
