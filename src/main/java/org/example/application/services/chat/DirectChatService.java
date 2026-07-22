package org.example.application.services.chat;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.chat.ChatEligibilityDTO;
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.exception.chat.ChatException;
import org.example.domain.entity.User;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.entity.chat.DirectConversationPair;
import org.example.domain.entity.chat.UserPrivacySettings;
import org.example.domain.enums.ConversationStatus;
import org.example.domain.enums.ConversationType;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;
import org.example.domain.repository.chat.ConversationRepository;
import org.example.domain.repository.chat.DirectConversationPairRepository;
import org.example.domain.repository.chat.UserFollowRepository;

@ApplicationScoped
@RequiredArgsConstructor
public class DirectChatService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final DirectConversationPairRepository directPairRepository;
    private final UserFollowRepository userFollowRepository;
    private final PrivacyService privacyService;
    private final ChatMapper chatMapper;

    @Transactional
    public ChatEligibilityDTO getEligibility(UUID initiatorId, UUID targetUserId) {
        validateDistinctUsers(initiatorId, targetUserId);
        User target = requireActiveUser(targetUserId);

        if (initiatorId.equals(targetUserId)) {
            return ChatEligibilityDTO.builder().canStartChat(false).reason("SELF").build();
        }

        UserPrivacySettings privacy = privacyService.getOrCreate(target.id);
        if (privacy.isPublicProfile()) {
            return ChatEligibilityDTO.builder().canStartChat(true).reason("PUBLIC").build();
        }

        if (userFollowRepository.isFollowing(initiatorId, target.id)) {
            return ChatEligibilityDTO.builder().canStartChat(true).reason("PUBLIC").build();
        }

        return ChatEligibilityDTO.builder().canStartChat(false).reason("NOT_FOLLOWING").build();
    }

    @Transactional
    public ConversationInboxItemDTO createOrGetDirect(UUID initiatorId, UUID targetUserId) {
        validateDistinctUsers(initiatorId, targetUserId);
        User initiator = requireActiveUser(initiatorId);
        User target = requireActiveUser(targetUserId);

        ChatEligibilityDTO eligibility = getEligibility(initiatorId, targetUserId);
        if (!eligibility.isCanStartChat()) {
            if ("NOT_FOLLOWING".equals(eligibility.getReason())) {
                throw ChatException.followersOnly();
            }
            throw ChatException.validation("Cannot start chat with this user");
        }

        return directPairRepository
                .findByUsers(initiatorId, targetUserId)
                .map(pair -> chatMapper.toInboxItem(pair.getConversation(), initiatorId))
                .orElseGet(() -> createDirectConversation(initiator, target));
    }

    private ConversationInboxItemDTO createDirectConversation(User initiator, User target) {
        Conversation conversation =
                Conversation.builder()
                        .type(ConversationType.DIRECT)
                        .status(ConversationStatus.ACTIVE)
                        .title(target.getFullName())
                        .build();
        conversationRepository.persist(conversation);

        persistParticipant(conversation, initiator);
        persistParticipant(conversation, target);

        UUID low = initiator.id.compareTo(target.id) <= 0 ? initiator.id : target.id;
        UUID high = initiator.id.compareTo(target.id) <= 0 ? target.id : initiator.id;
        User userLow = low.equals(initiator.id) ? initiator : target;
        User userHigh = high.equals(initiator.id) ? initiator : target;

        DirectConversationPair pair =
                DirectConversationPair.builder()
                        .conversation(conversation)
                        .userLow(userLow)
                        .userHigh(userHigh)
                        .build();
        directPairRepository.persist(pair);

        return chatMapper.toInboxItem(conversation, initiator.id);
    }

    private void persistParticipant(Conversation conversation, User user) {
        ConversationParticipant participant =
                ConversationParticipant.builder().conversation(conversation).user(user).build();
        participantRepository.persist(participant);
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId);
        if (user == null || user.getDeletedAt() != null || !"active".equalsIgnoreCase(user.getAccountStatus())) {
            throw ChatException.notFound("User not found");
        }
        return user;
    }

    private void validateDistinctUsers(UUID initiatorId, UUID targetUserId) {
        if (targetUserId == null) {
            throw ChatException.validation("targetUserId is required");
        }
        if (initiatorId.equals(targetUserId)) {
            throw ChatException.validation("Cannot start chat with yourself");
        }
    }
}
