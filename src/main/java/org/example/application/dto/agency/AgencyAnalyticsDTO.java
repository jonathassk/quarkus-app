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
    /** Volume bruto (soma de finalPrice) dos planejamentos com preço no funil ativo. */
    private BigDecimal forecastRevenue;
    /** Alias explícito do volume bruto (mesma base do forecast). */
    private BigDecimal grossVolume;
    /** Soma do valor cobrado pelo agente (finalPrice - baseCost) nos planejamentos com preço. */
    private BigDecimal estimatedMargin;
    /** Markup médio % (margem / base) nas propostas com base e final. */
    private Double avgMarginPercentage;
    private long activeClients;
    private long memberClients;
    private long guestClients;
    /** Tíquete médio (finalPrice) dos planejamentos com preço na base de volume. */
    private BigDecimal avgPackagePrice;
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
