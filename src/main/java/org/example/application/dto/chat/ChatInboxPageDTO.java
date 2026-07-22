package org.example.application.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInboxPageDTO {
    private List<ConversationInboxItemDTO> items;
    private String nextCursor;
}
