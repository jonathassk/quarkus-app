package org.example.application.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.ConversationType;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationInboxItemDTO {
    private String id;
    private ConversationType type;
    private String title;
    private String subtitle;
    private String avatarUrl;
    private String refId;
    private String lastMessageAt;
    private int unreadCount;
    private List<ChatParticipantDTO> participants;
    private Integer tripMemberCount;
    private boolean canSend;
}
