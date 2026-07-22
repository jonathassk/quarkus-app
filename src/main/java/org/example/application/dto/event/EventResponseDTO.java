package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventStatus;
import org.example.domain.enums.EventVisibility;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseDTO {
    private UUID id;
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private EventLocationDTO location;
    private EventVisibility visibility;
    private EventStatus status;
    private String coverImageUrl;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private EventSourceDTO source;
    private int participantCount;
    private EventParticipantRole myRole;
    private EventParticipantStatus myStatus;
    private boolean canEdit;
    private boolean canInvite;
}
