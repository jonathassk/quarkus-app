package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyOpportunityActivityDTO {
    private UUID id;
    private UUID opportunityId;
    private String activityType;
    private String title;
    private String body;
    private UUID actorUserId;
    private String actorLabel;
    private Instant createdAt;
}
