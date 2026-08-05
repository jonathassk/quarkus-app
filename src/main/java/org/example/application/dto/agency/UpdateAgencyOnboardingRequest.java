package org.example.application.dto.agency;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgencyOnboardingRequest {
    private String step;
    private Boolean skip;
    private Boolean complete;
    private UUID tripId;
    private UUID clientId;
    private String pricingModel;
}
