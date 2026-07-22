package org.example.application.dto.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.EventVisibility;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequestDTO {
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private EventLocationDTO location;
    private EventVisibility visibility;
    private EventSourceDTO source;
    private List<UUID> inviteUserIds;
    private String coverImageUrl;
}
