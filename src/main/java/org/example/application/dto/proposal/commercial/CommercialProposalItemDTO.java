package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialProposalItemDTO {
    private UUID id;
    private UUID optionId;
    private ProposalItemScope scope;
    private ProposalItemType itemType;
    private String name;
    private String subtitle;
    private Map<String, Object> details;
    private ItemPricingMode pricingMode;
    private String costCurrency;
    private Long costAmountMinor;
    private Long fxRateMicros;
    private LocalDate fxDate;
    private String fxSource;
    private Integer fxProtectionBps;
    private Long costMinor;
    private MarkupKind markupKind;
    private Long markupValueMinor;
    private Integer markupPercentBps;
    private Long supplierPublicPriceMinor;
    private MarkupKind commissionKind;
    private Long commissionValueMinor;
    private Integer commissionPercentBps;
    private long serviceFeeMinor;
    private Long clientPriceMinor;
    private Long expectedCommissionMinor;
    private Long expectedRevenueMinor;
    private Integer marginBps;
    private Integer markupBps;
    private String supplierName;
    private SupplierVisibility supplierVisibility;
    private boolean optional;
    private boolean hidePrice;
    private Instant quoteExpiresAt;
    private int sortOrder;
}
