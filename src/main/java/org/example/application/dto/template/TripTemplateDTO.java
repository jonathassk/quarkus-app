package org.example.application.dto.template;

import lombok.*;
import org.example.domain.enums.TripTemplateKind;
import org.example.domain.enums.TripTemplateScope;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripTemplateDTO {
    private UUID id;
    private TripTemplateScope scope;
    private TripTemplateKind kind;
    private UUID ownerId;
    private UUID agencyId;
    private String name;
    private String description;
    /** JSON do payload (TripRequestDTO ou TripSegmentDTO). */
    private String payload;
    private Instant createdAt;
    private Instant updatedAt;
}
