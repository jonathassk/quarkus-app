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
public class UpsertProposalItemRequest {
    private UUID id;
    private ProposalItemScope scope;
    private UUID optionId;
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
    private Long serviceFeeMinor;
    private Long clientPriceMinor;
    private String supplierName;
    private SupplierVisibility supplierVisibility;
    private Boolean optional;
    private Boolean hidePrice;
    private Instant quoteExpiresAt;
    private Integer sortOrder;
}
