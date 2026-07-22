package org.example.application.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripChatStatusDTO {
    private boolean exists;
    private ConversationInboxItemDTO conversation;
    private int memberCount;
    private boolean autoCreateEligible;
    private boolean canCreateManual;
}
