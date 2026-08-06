package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyOpportunityTaskDTO {
    private UUID id;
    private UUID opportunityId;
    private String title;
    private String status;
    private Instant dueAt;
    private UUID assigneeUserId;
    private String assigneeName;
    private String taskType;
    private String actionKind;
    private String note;
    private String waitingOn;
    @com.fasterxml.jackson.annotation.JsonProperty("isNextAction")
    private Boolean nextAction;
    private String origin;
    private String priority;
    private String completionOutcome;
    private UUID completedByUserId;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean overdue;
}
