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
    /** Reinicia o fluxo guiado (nova proposta) limpando conclusão e trip/client atuais. */
    private Boolean restart;
    private UUID tripId;
    private UUID clientId;
    private String pricingModel;
}
