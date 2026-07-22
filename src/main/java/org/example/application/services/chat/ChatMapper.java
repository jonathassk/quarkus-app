package org.example.application.services.chat;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.chat.*;
import org.example.domain.entity.User;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.entity.chat.Message;
import org.example.domain.enums.ConversationStatus;
import org.example.domain.enums.ConversationType;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class ChatMapper {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final TripRepository tripRepository;
    private final ConversationParticipantRepository participantRepository;

    public ChatParticipantDTO toParticipant(User user) {
        if (user == null) {
            return null;
        }
        return ChatParticipantDTO.builder()
                .userId(user.id)
                .fullName(user.getFullName())
                .avatarUrl(user.getProfilePictureUrl())
                .build();
    }

    public MessageDTO toMessage(Message message) {
        User sender = message.getSender();
        String content = message.isDeleted() ? "[Mensagem removida]" : message.getContent();
        return MessageDTO.builder()
                .id(message.getId().toString())
                .conversationId(message.getConversation().getId().toString())
                .senderId(sender != null ? sender.id : null)
                .senderName(sender != null ? sender.getFullName() : null)
                .senderAvatarUrl(sender != null ? sender.getProfilePictureUrl() : null)
                .content(content)
                .contentType(message.getContentType())
                .createdAt(message.getCreatedAt() != null ? ISO.format(message.getCreatedAt()) : null)
                .editedAt(message.getEditedAt() != null ? ISO.format(message.getEditedAt()) : null)
                .build();
    }

    public ConversationInboxItemDTO toInboxItem(Conversation conversation, UUID viewerUserId) {
        List<ConversationParticipant> participants =
                participantRepository.findActiveByConversation(conversation.getId());
        List<ChatParticipantDTO> participantDtos =
                participants.stream()
                        .map(p -> toParticipant(p.getUser()))
                        .filter(p -> p != null)
                        .toList();

        int unread =
                participants.stream()
                        .filter(p -> p.getUser() != null && p.getUser().id.equals(viewerUserId))
                        .mapToInt(ConversationParticipant::getUnreadCount)
                        .findFirst()
                        .orElse(0);

        boolean canSend =
                conversation.getStatus() == ConversationStatus.ACTIVE
                        && participants.stream()
                                .anyMatch(
                                        p ->
                                                p.getUser() != null
                                                        && p.getUser().id.equals(viewerUserId)
                                                        && p.isActive());

        Integer tripMemberCount = null;
        if (conversation.getType() == ConversationType.TRIP && conversation.getRefId() != null) {
            tripMemberCount = tripRepository.countTripMembers(conversation.getRefId());
        }

        String subtitle = conversation.getLastMessagePreview();
        String avatarUrl = resolveAvatar(conversation, participants, viewerUserId);

        return ConversationInboxItemDTO.builder()
                .id(conversation.getId().toString())
                .type(conversation.getType())
                .title(conversation.getTitle())
                .subtitle(subtitle)
                .avatarUrl(avatarUrl)
                .refId(conversation.getRefId() != null ? conversation.getRefId().toString() : null)
                .lastMessageAt(
                        conversation.getLastMessageAt() != null
                                ? ISO.format(conversation.getLastMessageAt())
                                : null)
                .unreadCount(unread)
                .participants(participantDtos)
                .tripMemberCount(tripMemberCount)
                .canSend(canSend)
                .build();
    }

    private String resolveAvatar(
            Conversation conversation,
            List<ConversationParticipant> participants,
            UUID viewerUserId) {
        if (conversation.getType() == ConversationType.DIRECT) {
            return participants.stream()
                    .filter(p -> p.getUser() != null && !p.getUser().id.equals(viewerUserId))
                    .map(p -> p.getUser().getProfilePictureUrl())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    public List<ChatParticipantDTO> mapParticipants(List<ConversationParticipant> participants) {
        List<ChatParticipantDTO> result = new ArrayList<>();
        for (ConversationParticipant participant : participants) {
            ChatParticipantDTO dto = toParticipant(participant.getUser());
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }
}
