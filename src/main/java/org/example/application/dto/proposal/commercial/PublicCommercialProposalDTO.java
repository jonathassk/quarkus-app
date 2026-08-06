package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.PriceVisibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicCommercialProposalDTO {
    private UUID proposalId;
    private UUID versionId;
    private int versionNumber;
    private String status;
    private String currency;
    private PriceVisibility priceVisibility;
    private Instant expiresAt;
    private boolean expired;
    private String recommendationNote;
    private String agencyName;
    private String agencyLogoUrl;
    private String agencyPrimaryColor;
    private Boolean poweredByBaggagi;
    private String clientName;
    private List<PublicOptionDTO> options;
    private List<PublicAddOnDTO> addOns;
    private boolean changeRequestAllowed;
}
