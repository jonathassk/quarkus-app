package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertOpportunityTaskRequest {
    private String title;
    private String status;
    private Instant dueAt;
    private UUID assigneeUserId;
}
