package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteOpportunityTaskRequest {
    private String outcome;
    private String note;
    /** Optional next step after completion. */
    private String nextActionKind;
    private String nextTitle;
    private Instant nextDueAt;
    private UUID nextAssigneeUserId;
    private String nextNote;
    /** When false, do not create/set a next action. Default true if nextActionKind/nextDueAt present. */
    private Boolean defineNext;
}
