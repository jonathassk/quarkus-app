package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.EventVisibility;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventRequestDTO {
    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private EventLocationDTO location;
    private EventVisibility visibility;
    private String coverImageUrl;
}
