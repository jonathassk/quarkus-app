package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicAddOnDTO {
    private UUID id;
    private String name;
    private String description;
    private long priceMinor;
    private String pricingUnit;
    private List<UUID> eligibleOptionIds;
    private boolean required;
}
