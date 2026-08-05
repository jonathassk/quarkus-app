package org.example.application.dto.agency;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkOpportunityLostRequest {
    private String lostReason;
}
