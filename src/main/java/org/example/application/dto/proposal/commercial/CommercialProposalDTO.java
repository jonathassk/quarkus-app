package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.PriceVisibility;
import org.example.domain.enums.ProposalFormat;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialProposalDTO {
    private UUID id;
    private UUID agencyId;
    private UUID opportunityId;
    private UUID clientId;
    private String clientName;
    private UUID consultantId;
    private String shareCode;
    private String presentationCurrency;
    private PriceVisibility priceVisibility;
    private ProposalFormat format;
    private UUID currentVersionId;
    private CommercialProposalVersionDTO currentVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
