package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendCommercialProposalRequest {
    private String clientEmail;
    private String clientName;
    private Boolean allowNegotiation;
    private Instant expiresAt;
}
