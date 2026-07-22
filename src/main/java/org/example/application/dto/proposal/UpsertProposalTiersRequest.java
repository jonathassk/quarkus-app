package org.example.application.dto.proposal;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertProposalTiersRequest {
    private List<TierItem> tiers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierItem {
        private String code;
        private String label;
        private BigDecimal priceDelta;
        private Integer sortOrder;
    }
}
