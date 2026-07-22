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
public class EventPostResponseDTO {
    private UUID postId;
    private UUID eventId;
    private UUID authorId;
    private String authorName;
    private String text;
    private String imageUrl;
    private String location;
    private Instant postedAt;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
}
