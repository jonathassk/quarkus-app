package org.example.application.dto.agency;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyAnalyticsDTO {
    private long proposalsSent;
    private long proposalsApproved;
    private long proposalsRejected;
    private long proposalsDraft;
    private long proposalsLost;
    private double conversionRate;
    private BigDecimal forecastRevenue;
    private List<DestinationStat> topDestinations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationStat {
        private String cityOrName;
        private long count;
    }
}
