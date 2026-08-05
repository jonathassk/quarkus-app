package org.example.application.dto.agency;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyOnboardingDTO {
    private String step;
    private Boolean completed;
    private Boolean skipped;
    private java.time.Instant completedAt;
    private java.time.Instant skippedAt;
    private Boolean demoDataActive;
    private String planType;
    private UUID tripId;
    private UUID clientId;
    private String pricingModel;
}
