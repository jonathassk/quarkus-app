package org.example.application.services.chat;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.example.application.exception.chat.ChatException;
import org.example.domain.entity.Trip;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.enums.ConversationStatus;
import org.example.domain.enums.ConversationType;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;
import org.example.domain.repository.chat.ConversationRepository;
import org.example.domain.repository.event.EventParticipantRepository;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final TripRepository tripRepository;
    private final EventParticipantRepository eventParticipantRepository;

    public Conversation requireConversation(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId);
        if (conversation == null) {
            throw ChatException.notFound("Conversation not found");
        }
        return conversation;
    }

    public ConversationParticipant requireActiveParticipant(UUID conversationId, UUID userId) {
        Optional<ConversationParticipant> participant =
                participantRepository.findActive(conversationId, userId);
        if (participant.isEmpty()) {
            throw ChatException.notFound("Conversation not found");
        }
        return participant.get();
    }

    public void assertCanRead(UUID conversationId, UUID userId) {
        requireActiveParticipant(conversationId, userId);
    }

    public void assertCanSendMessage(UUID conversationId, UUID userId) {
        ConversationParticipant participant = requireActiveParticipant(conversationId, userId);
        Conversation conversation = participant.getConversation();

        if (conversation.getStatus() == ConversationStatus.ARCHIVED) {
            throw ChatException.archived();
        }

        if (conversation.getType() == ConversationType.TRIP && conversation.getRefId() != null) {
            if (!tripRepository.isUserLinkedToTrip(conversation.getRefId(), userId)) {
                throw ChatException.notParticipant();
            }
        }

        if (conversation.getType() == ConversationType.EVENT && conversation.getRefUuid() != null) {
            if (!canSendEventChat(conversation.getRefUuid(), userId)) {
                throw ChatException.notParticipant();
            }
        }
    }

    private boolean canSendEventChat(UUID eventId, UUID userId) {
        return eventParticipantRepository.isOrganizer(eventId, userId)
                || eventParticipantRepository
                        .findByEventAndUser(eventId, userId)
                        .map(
                                p ->
                                        p.getStatus()
                                                == org.example.domain.enums.EventParticipantStatus.ACCEPTED)
                        .orElse(false);
    }

    public void assertTripMember(Trip trip, UUID userId) {
        if (!tripRepository.isUserLinkedToTrip(trip.id, userId)) {
            throw ChatException.notFound("Trip not found");
        }
    }

    public void assertTripOwner(Trip trip, UUID userId) {
        if (trip.getCreatedBy() == null || !trip.getCreatedBy().id.equals(userId)) {
            throw ChatException.forbidden("Only the trip owner can perform this action");
        }
    }
}
