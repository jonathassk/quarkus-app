package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPostCommentResponseDTO {
    private UUID commentId;
    private UUID postId;
    private UUID authorId;
    private String authorName;
    private String text;
    private Instant createdAt;
}
