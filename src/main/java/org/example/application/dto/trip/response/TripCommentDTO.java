package org.example.application.dto.trip.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.TripCommentTargetType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCommentDTO {
    private UUID id;
    private UUID tripId;
    private TripCommentTargetType targetType;
    private String targetId;
    private UUID authorId;
    private String authorName;
    private String body;
    private Instant resolvedAt;
    private Instant createdAt;
}
