package org.example.application.services.chat;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.dto.chat.TripChatStatusDTO;
import org.example.application.exception.chat.ChatException;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.enums.ConversationStatus;
import org.example.domain.enums.ConversationType;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;
import org.example.domain.repository.chat.ConversationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripChatService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatAuthorizationService authorizationService;
    private final ChatMapper chatMapper;

    @ConfigProperty(name = "chat.enabled", defaultValue = "true")
    boolean chatEnabled;

    @Transactional
    public TripChatStatusDTO getStatus(UUID tripId, UUID userId) {
        Trip trip = requireTrip(tripId);
        authorizationService.assertTripMember(trip, userId);

        int memberCount = tripRepository.countTripMembers(tripId);
        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefId(ConversationType.TRIP, tripId);

        if (existing.isEmpty() && memberCount >= 2) {
            ensureConversationIfEligible(tripId);
            existing = conversationRepository.findByTypeAndRefId(ConversationType.TRIP, tripId);
        }

        ConversationInboxItemDTO conversationDto = null;
        if (existing.isPresent()) {
            conversationDto = chatMapper.toInboxItem(existing.get(), userId);
        }

        boolean isOwner = trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId);

        return TripChatStatusDTO.builder()
                .exists(existing.isPresent())
                .conversation(conversationDto)
                .memberCount(memberCount)
                .autoCreateEligible(memberCount >= 2)
                .canCreateManual(isOwner && memberCount == 1 && existing.isEmpty())
                .build();
    }

    @Transactional
    public ConversationInboxItemDTO createManual(UUID tripId, UUID ownerId) {
        if (!chatEnabled) {
            throw ChatException.forbidden("Chat is disabled");
        }
        Trip trip = requireTrip(tripId);
        authorizationService.assertTripOwner(trip, ownerId);

        int memberCount = tripRepository.countTripMembers(tripId);
        if (memberCount != 1) {
            throw ChatException.validation("Manual chat creation is only allowed for solo trips");
        }

        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefId(ConversationType.TRIP, tripId);
        if (existing.isPresent()) {
            throw ChatException.alreadyExists("Trip chat already exists");
        }

        Conversation conversation = createTripConversation(trip);
        return chatMapper.toInboxItem(conversation, ownerId);
    }

    @Transactional
    public void ensureConversationIfEligible(UUID tripId) {
        if (!chatEnabled) {
            return;
        }
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            return;
        }
        int memberCount = tripRepository.countTripMembers(tripId);
        if (memberCount < 2) {
            return;
        }
        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefId(ConversationType.TRIP, tripId);
        if (existing.isPresent()) {
            syncParticipants(existing.get(), tripId);
            return;
        }
        createTripConversation(trip);
    }

    @Transactional
    public void onMemberAdded(UUID tripId, UUID userId) {
        if (!chatEnabled) {
            return;
        }
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            return;
        }
        int memberCount = tripRepository.countTripMembers(tripId);
        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefId(ConversationType.TRIP, tripId);
        if (existing.isEmpty()) {
            if (memberCount >= 2) {
                createTripConversation(trip);
            }
            return;
        }
        addParticipantIfMissing(existing.get(), userId);
    }

    @Transactional
    public void onMemberRemoved(UUID tripId, UUID userId) {
        if (!chatEnabled) {
            return;
        }
        conversationRepository
                .findByTypeAndRefId(ConversationType.TRIP, tripId)
                .ifPresent(conversation -> markParticipantLeft(conversation, userId));
    }

    @Transactional
    public void archiveConversation(UUID tripId) {
        if (!chatEnabled) {
            return;
        }
        conversationRepository
                .findByTypeAndRefId(ConversationType.TRIP, tripId)
                .ifPresent(
                        conversation -> {
                            conversation.setStatus(ConversationStatus.ARCHIVED);
                            conversationRepository.persist(conversation);
                        });
    }

    private Conversation createTripConversation(Trip trip) {
        Conversation conversation =
                Conversation.builder()
                        .type(ConversationType.TRIP)
                        .refId(trip.id)
                        .status(ConversationStatus.ACTIVE)
                        .title(trip.getName())
                        .build();
        conversationRepository.persist(conversation);
        syncParticipants(conversation, trip.id);
        log.info("Created trip chat conversationId={} tripId={}", conversation.getId(), trip.id);
        return conversation;
    }

    private void syncParticipants(Conversation conversation, UUID tripId) {
        List<UUID> memberIds = tripRepository.listTripMemberUserIds(tripId);
        for (UUID memberId : memberIds) {
            addParticipantIfMissing(conversation, memberId);
        }
    }

    private void addParticipantIfMissing(Conversation conversation, UUID userId) {
        Optional<ConversationParticipant> existing =
                participantRepository.findActive(conversation.getId(), userId);
        if (existing.isPresent()) {
            return;
        }

        Optional<ConversationParticipant> leftParticipant =
                participantRepository
                        .find("conversation.id = ?1 and user.id = ?2", conversation.getId(), userId)
                        .firstResultOptional();
        if (leftParticipant.isPresent() && leftParticipant.get().getLeftAt() != null) {
            ConversationParticipant participant = leftParticipant.get();
            participant.setLeftAt(null);
            participant.setJoinedAt(Instant.now());
            participantRepository.persist(participant);
            return;
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            return;
        }
        ConversationParticipant participant =
                ConversationParticipant.builder().conversation(conversation).user(user).build();
        participantRepository.persist(participant);
    }

    private void markParticipantLeft(Conversation conversation, UUID userId) {
        participantRepository
                .findActive(conversation.getId(), userId)
                .ifPresent(
                        participant -> {
                            participant.setLeftAt(Instant.now());
                            participantRepository.persist(participant);
                        });
    }

    private Trip requireTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw ChatException.notFound("Trip not found");
        }
        return trip;
    }
}
