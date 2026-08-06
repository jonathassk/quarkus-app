package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeferOpportunityTaskRequest {
    private Instant dueAt;
}
