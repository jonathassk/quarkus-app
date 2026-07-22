package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.application.dto.chat.ConversationInboxItemDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventChatStatusDTO {
    private boolean exists;
    private ConversationInboxItemDTO conversation;
    private int participantCount;
    private boolean autoCreateEligible;
    private boolean canCreateManual;
}
