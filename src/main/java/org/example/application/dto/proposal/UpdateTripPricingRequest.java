package org.example.application.dto.proposal;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripPricingRequest {
    private BigDecimal baseCost;
    /** Override; se null, usa markup da agência. */
    private BigDecimal markupPercentage;
    /**
     * Breakdown do custo (voo, hospedagem…). Quando presente, {@code baseCost}
     * é recalculado como a soma dos amounts.
     */
    private List<BaseCostItemDTO> baseCostItems;
}
