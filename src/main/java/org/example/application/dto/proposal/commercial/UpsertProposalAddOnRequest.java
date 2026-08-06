package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertProposalAddOnRequest {
    private UUID id;
    private String name;
    private String description;
    private Long priceMinor;
    private String pricingUnit;
    private Integer quantityDefault;
    private List<UUID> eligibleOptionIds;
    private Boolean required;
    private Boolean optional;
    private Instant expiresAt;
    private Integer sortOrder;
}
