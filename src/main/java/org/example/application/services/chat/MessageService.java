package org.example.application.services.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.chat.*;
import org.example.application.exception.chat.ChatException;
import org.example.domain.entity.User;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.Message;
import org.example.domain.enums.MessageContentType;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.chat.ConversationRepository;
import org.example.domain.repository.chat.ConversationParticipantRepository;
import org.example.domain.repository.chat.MessageRepository;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 50;
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]+>");

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService authorizationService;
    private final ChatMapper chatMapper;
    private final ChatBroadcastService broadcastService;

    @Transactional
    public MessageDTO sendMessage(UUID conversationId, UUID senderId, SendMessageRequestDTO request) {
        authorizationService.assertCanSendMessage(conversationId, senderId);

        String content = validateContent(request != null ? request.getContent() : null);
        MessageContentType contentType = parseContentType(request != null ? request.getContentType() : null);

        User sender = userRepository.findById(senderId);
        if (sender == null) {
            throw ChatException.notFound("User not found");
        }

        Conversation conversation = conversationRepository.findById(conversationId);
        Message message =
                Message.builder()
                        .conversation(conversation)
                        .sender(sender)
                        .content(content)
                        .contentType(contentType)
                        .build();
        messageRepository.persist(message);

        String preview = content.length() > 120 ? content.substring(0, 117) + "..." : content;
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setLastMessagePreview(preview);
        conversationRepository.persist(conversation);

        participantRepository.incrementUnreadForOthers(conversationId, senderId);

        MessageDTO dto = chatMapper.toMessage(message);
        broadcastService.broadcastMessageNew(conversationId, dto);
        return dto;
    }

    @Transactional
    public ChatMessagesPageDTO getMessages(UUID conversationId, UUID userId, Integer limit, String before) {
        authorizationService.assertCanRead(conversationId, userId);
        int pageSize = normalizeLimit(limit);

        Instant beforeAt = null;
        UUID beforeId = null;
        if (before != null && !before.isBlank()) {
            UUID messageId = parseUuid(before, "before");
            Message cursorMessage =
                    messageRepository
                            .findInConversation(messageId, conversationId)
                            .orElseThrow(() -> ChatException.validation("Invalid cursor"));
            beforeAt = cursorMessage.getCreatedAt();
            beforeId = cursorMessage.getId();
        }

        List<Message> rows = messageRepository.findPage(conversationId, beforeAt, beforeId, pageSize + 1);
        String nextCursor = null;
        if (rows.size() > pageSize) {
            nextCursor = rows.get(pageSize).getId().toString();
            rows = rows.subList(0, pageSize);
        }

        List<MessageDTO> messages = rows.stream().map(chatMapper::toMessage).toList();
        return ChatMessagesPageDTO.builder().messages(messages).nextCursor(nextCursor).build();
    }

    @Transactional
    public void markAsRead(UUID conversationId, UUID userId, MarkReadRequestDTO request) {
        authorizationService.assertCanRead(conversationId, userId);
        if (request != null && request.getLastReadMessageId() != null) {
            UUID messageId = parseUuid(request.getLastReadMessageId(), "lastReadMessageId");
            messageRepository
                    .findInConversation(messageId, conversationId)
                    .orElseThrow(() -> ChatException.validation("Invalid lastReadMessageId"));
        }
        participantRepository.resetUnread(conversationId, userId);
    }

    private String validateContent(String raw) {
        if (raw == null) {
            throw ChatException.validation("content is required");
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC).trim();
        if (normalized.isEmpty()) {
            throw ChatException.validation("content must not be empty");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw ChatException.validation("content must be at most " + MAX_CONTENT_LENGTH + " characters");
        }
        if (HTML_PATTERN.matcher(normalized).find()) {
            throw ChatException.validation("content must not contain HTML tags");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '\0' || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
                throw ChatException.validation("content contains invalid control characters");
            }
        }
        return normalized;
    }

    private MessageContentType parseContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return MessageContentType.TEXT;
        }
        try {
            MessageContentType type = MessageContentType.valueOf(raw.trim().toUpperCase());
            if (type == MessageContentType.SYSTEM) {
                throw ChatException.validation("SYSTEM content type is not allowed from clients");
            }
            return type;
        } catch (IllegalArgumentException e) {
            throw ChatException.validation("Invalid contentType");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw ChatException.validation("limit must be positive");
        }
        if (limit > MAX_LIMIT) {
            throw ChatException.validation("limit must be at most " + MAX_LIMIT);
        }
        return limit;
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw ChatException.validation("Invalid " + field);
        }
    }
}
