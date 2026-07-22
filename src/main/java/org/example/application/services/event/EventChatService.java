package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.dto.event.EventChatStatusDTO;
import org.example.application.exception.chat.ChatException;
import org.example.application.exception.event.EventException;
import org.example.application.services.chat.ChatMapper;
import org.example.domain.entity.User;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.entity.event.Event;
import org.example.domain.enums.ConversationStatus;
import org.example.domain.enums.ConversationType;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;
import org.example.domain.repository.chat.ConversationRepository;
import org.example.domain.repository.event.EventParticipantRepository;
import org.example.domain.repository.event.EventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class EventChatService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository chatParticipantRepository;
    private final EventAuthorizationService authorizationService;
    private final ChatMapper chatMapper;

    @ConfigProperty(name = "chat.enabled", defaultValue = "true")
    boolean chatEnabled;

    @Transactional
    public EventChatStatusDTO getStatus(UUID eventId, UUID userId) {
        Event event = authorizationService.assertCanView(eventId, userId);

        int acceptedCount = (int) participantRepository.countAcceptedByEventId(eventId);
        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefUuid(ConversationType.EVENT, eventId);

        if (existing.isEmpty() && acceptedCount >= 2) {
            ensureConversationIfEligible(eventId);
            existing = conversationRepository.findByTypeAndRefUuid(ConversationType.EVENT, eventId);
        }

        ConversationInboxItemDTO conversationDto = null;
        if (existing.isPresent()) {
            conversationDto = chatMapper.toInboxItem(existing.get(), userId);
        }

        boolean isOrganizer = participantRepository.isOrganizer(eventId, userId);

        return EventChatStatusDTO.builder()
                .exists(existing.isPresent())
                .conversation(conversationDto)
                .participantCount((int) participantRepository.countByEventId(eventId))
                .autoCreateEligible(acceptedCount >= 2)
                .canCreateManual(isOrganizer && acceptedCount == 1 && existing.isEmpty())
                .build();
    }

    @Transactional
    public ConversationInboxItemDTO createManual(UUID eventId, UUID userId) {
        if (!chatEnabled) {
            throw ChatException.forbidden("Chat is disabled");
        }
        Event event = authorizationService.assertCanView(eventId, userId);
        if (!participantRepository.isOrganizer(eventId, userId)) {
            throw EventException.notFound();
        }

        int acceptedCount = (int) participantRepository.countAcceptedByEventId(eventId);
        if (acceptedCount != 1) {
            throw ChatException.validation("Manual chat creation is only allowed when organizer is the only accepted participant");
        }

        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefUuid(ConversationType.EVENT, eventId);
        if (existing.isPresent()) {
            throw ChatException.alreadyExists("Event chat already exists");
        }

        Conversation conversation = createEventConversation(event);
        return chatMapper.toInboxItem(conversation, userId);
    }

    @Transactional
    public void ensureConversationIfEligible(UUID eventId) {
        if (!chatEnabled) {
            return;
        }
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            return;
        }
        int acceptedCount = (int) participantRepository.countAcceptedByEventId(eventId);
        if (acceptedCount < 2) {
            return;
        }
        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefUuid(ConversationType.EVENT, eventId);
        if (existing.isPresent()) {
            syncParticipants(existing.get(), eventId);
            return;
        }
        createEventConversation(event);
    }

    @Transactional
    public void onParticipantAccepted(UUID eventId, UUID userId) {
        if (!chatEnabled) {
            return;
        }
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            return;
        }
        int acceptedCount = (int) participantRepository.countAcceptedByEventId(eventId);
        Optional<Conversation> existing =
                conversationRepository.findByTypeAndRefUuid(ConversationType.EVENT, eventId);
        if (existing.isEmpty()) {
            if (acceptedCount >= 2) {
                createEventConversation(event);
            }
            return;
        }
        addParticipantIfMissing(existing.get(), userId);
    }

    @Transactional
    public void onParticipantLeft(UUID eventId, UUID userId) {
        if (!chatEnabled) {
            return;
        }
        conversationRepository
                .findByTypeAndRefUuid(ConversationType.EVENT, eventId)
                .ifPresent(conversation -> markParticipantLeft(conversation, userId));
    }

    @Transactional
    public void archiveConversation(UUID eventId) {
        if (!chatEnabled) {
            return;
        }
        conversationRepository
                .findByTypeAndRefUuid(ConversationType.EVENT, eventId)
                .ifPresent(
                        conversation -> {
                            conversation.setStatus(ConversationStatus.ARCHIVED);
                            conversationRepository.persist(conversation);
                        });
    }

    private Conversation createEventConversation(Event event) {
        Conversation conversation =
                Conversation.builder()
                        .type(ConversationType.EVENT)
                        .refUuid(event.getId())
                        .status(ConversationStatus.ACTIVE)
                        .title(event.getTitle())
                        .build();
        conversationRepository.persist(conversation);
        syncParticipants(conversation, event.getId());
        log.info("Created event chat conversationId={} eventId={}", conversation.getId(), event.getId());
        return conversation;
    }

    private void syncParticipants(Conversation conversation, UUID eventId) {
        List<UUID> memberIds = participantRepository.listAcceptedUserIds(eventId);
        for (UUID memberId : memberIds) {
            addParticipantIfMissing(conversation, memberId);
        }
    }

    private void addParticipantIfMissing(Conversation conversation, UUID userId) {
        Optional<ConversationParticipant> existing =
                chatParticipantRepository.findActive(conversation.getId(), userId);
        if (existing.isPresent()) {
            return;
        }

        Optional<ConversationParticipant> leftParticipant =
                chatParticipantRepository
                        .find("conversation.id = ?1 and user.id = ?2", conversation.getId(), userId)
                        .firstResultOptional();
        if (leftParticipant.isPresent() && leftParticipant.get().getLeftAt() != null) {
            ConversationParticipant participant = leftParticipant.get();
            participant.setLeftAt(null);
            participant.setJoinedAt(Instant.now());
            chatParticipantRepository.persist(participant);
            return;
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            return;
        }
        ConversationParticipant participant =
                ConversationParticipant.builder().conversation(conversation).user(user).build();
        chatParticipantRepository.persist(participant);
    }

    private void markParticipantLeft(Conversation conversation, UUID userId) {
        chatParticipantRepository
                .findActive(conversation.getId(), userId)
                .ifPresent(
                        participant -> {
                            participant.setLeftAt(Instant.now());
                            chatParticipantRepository.persist(participant);
                        });
    }
}
