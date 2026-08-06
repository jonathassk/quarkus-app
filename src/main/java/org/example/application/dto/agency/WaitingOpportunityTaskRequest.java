package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingOpportunityTaskRequest {
    /** CLIENT | SUPPLIER | CONSULTANT | FINANCE | PARTNER | OTHER */
    private String waitingOn;
    /** Review date — required. */
    private Instant dueAt;
    private String note;
}
