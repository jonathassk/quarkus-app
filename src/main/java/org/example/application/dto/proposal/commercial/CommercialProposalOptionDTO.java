package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.ProposalOptionPosition;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialProposalOptionDTO {
    private UUID id;
    private UUID tripId;
    private ProposalOptionPosition position;
    private int sortOrder;
    private boolean recommended;
    private boolean hidden;
    private String name;
    private String subtitle;
    private String shortDescription;
    private String coverImageUrl;
    private List<String> includes;
    private List<String> excludes;
    private String paymentConditions;
    private long supplierCostMinor;
    private long markupAmountMinor;
    private long serviceFeeMinor;
    private long commissionMinor;
    private long clientPriceMinor;
    private long expectedRevenueMinor;
    private Integer marginBps;
    private Integer markupBps;
    /** Aviso quando margem < mínimo da agência. */
    private String marginWarning;
    private List<CommercialProposalItemDTO> items;
    private FinancialSummaryDTO financialSummary;
}
