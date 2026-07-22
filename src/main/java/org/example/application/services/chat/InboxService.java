package org.example.application.services.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.chat.ChatInboxPageDTO;
import org.example.application.dto.chat.ConversationInboxItemDTO;
import org.example.application.exception.chat.ChatException;
import org.example.domain.entity.chat.Conversation;
import org.example.domain.entity.chat.ConversationParticipant;
import org.example.domain.repository.chat.ConversationParticipantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class InboxService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 50;

    private final ConversationParticipantRepository participantRepository;
    private final ChatMapper chatMapper;
    private final ChatAuthorizationService authorizationService;

    @Transactional
    public ChatInboxPageDTO getInbox(UUID userId, Integer limit, String cursor) {
        int pageSize = normalizeLimit(limit);
        List<ConversationParticipant> rows = participantRepository.findActiveByUser(userId, pageSize + 1);

        List<ConversationInboxItemDTO> items = new ArrayList<>();
        String nextCursor = null;
        int count = 0;
        for (ConversationParticipant row : rows) {
            if (count >= pageSize) {
                nextCursor = row.getConversation().getId().toString();
                break;
            }
            if (cursor != null && row.getConversation().getId().toString().equals(cursor)) {
                continue;
            }
            items.add(chatMapper.toInboxItem(row.getConversation(), userId));
            count++;
        }

        return ChatInboxPageDTO.builder().items(items).nextCursor(nextCursor).build();
    }

    @Transactional
    public ConversationInboxItemDTO getConversationDetail(UUID conversationId, UUID userId) {
        authorizationService.assertCanRead(conversationId, userId);
        Conversation conversation = authorizationService.requireConversation(conversationId);
        return chatMapper.toInboxItem(conversation, userId);
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
}
