package org.example.application.dto.chat;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.MessageContentType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String id;
    private String conversationId;
    private UUID senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private MessageContentType contentType;
    private String createdAt;
    private String editedAt;
}
