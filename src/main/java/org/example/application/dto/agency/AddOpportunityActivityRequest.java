package org.example.application.dto.agency;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOpportunityActivityRequest {
    private String activityType;
    private String title;
    private String body;
}
