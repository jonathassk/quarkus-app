package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyAgendaItemDTO {
    private UUID taskId;
    private UUID opportunityId;
    private String title;
    private String actionKind;
    private String status;
    private Instant dueAt;
    private Boolean overdue;
    @com.fasterxml.jackson.annotation.JsonProperty("isNextAction")
    private Boolean nextAction;
    private String priority;
    private String waitingOn;
    private String note;

    private UUID assigneeUserId;
    private String assigneeName;

    private String clientName;
    private String clientPhone;
    private String destination;
    private String opportunityTitle;
    private String stage;
    private String recentEvent;
    private Instant recentEventAt;
    private Boolean suggestAdvanceFollowUp;
}
