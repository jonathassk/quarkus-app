package org.example.application.dto.chat;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipantDTO {
    private UUID userId;
    private String fullName;
    private String avatarUrl;
}
