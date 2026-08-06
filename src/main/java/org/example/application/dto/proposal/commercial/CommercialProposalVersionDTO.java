package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.CommercialProposalStatus;
import org.example.domain.enums.PricingEditMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialProposalVersionDTO {
    private UUID id;
    private int versionNumber;
    private CommercialProposalStatus status;
    private PricingEditMode pricingEditMode;
    private Instant expiresAt;
    private String clientEmail;
    private String clientName;
    private boolean allowNegotiation;
    private String recommendationNote;
    private Instant sentAt;
    private Instant lastViewedAt;
    private int viewCount;
    private String rejectReason;
    private List<String> changeRequestTypes;
    private String changeRequestMessage;
    private Instant changeRequestedAt;
    private String changeRequestedByName;
    private String changeRequestedByEmail;
    private String belowMinimumJustification;
    private Integer agencyMinMarginBps;
    private List<CommercialProposalOptionDTO> options;
    private List<CommercialProposalItemDTO> commonItems;
    private List<CommercialProposalAddOnDTO> addOns;
    private List<CommercialProposalAdjustmentDTO> adjustments;
}
