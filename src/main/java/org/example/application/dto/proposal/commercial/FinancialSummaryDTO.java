package org.example.application.dto.proposal.commercial;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSummaryDTO {
    private long supplierCostMinor;
    private long markupAmountMinor;
    private long serviceFeeMinor;
    private long commissionMinor;
    private long clientPriceMinor;
    private long expectedRevenueMinor;
    private Integer marginBps;
    private Integer markupBps;
}
