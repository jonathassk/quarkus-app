package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialProposalAddOnDTO {
    private UUID id;
    private String name;
    private String description;
    private long priceMinor;
    private String pricingUnit;
    private int quantityDefault;
    private List<UUID> eligibleOptionIds;
    private boolean required;
    private boolean optional;
    private Instant expiresAt;
    private int sortOrder;
}
