package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.ItemPricingMode;
import org.example.domain.enums.MarkupKind;
import org.example.domain.enums.ProposalOptionPosition;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertProposalOptionRequest {
    private String name;
    private String subtitle;
    private String shortDescription;
    private String coverImageUrl;
    private ProposalOptionPosition position;
    private Boolean recommended;
    private Boolean hidden;
    private List<String> includes;
    private List<String> excludes;
    private String paymentConditions;
    private Integer sortOrder;
    private Long quickCostMinor;
    private MarkupKind quickMarkupKind;
    private Long quickMarkupValueMinor;
    private Integer quickMarkupPercentBps;
    private Long quickServiceFeeMinor;
    private Long quickClientPriceMinor;
    private ItemPricingMode quickPricingMode;
}
