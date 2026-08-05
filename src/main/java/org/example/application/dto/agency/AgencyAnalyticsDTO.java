package org.example.application.dto.agency;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

    /** Período aplicado: ALL | MONTH | QUARTER | YEAR*/
    private String period;
    /** Variação % do volume vs período anterior (null se sem base). */
    private Double grossVolumeDeltaPct;
    /** Variação % da margem vs período anterior. */
    private Double estimatedMarginDeltaPct;
    /** Variação em pontos percentuais da conversão vs período anterior. */
    private Double conversionRateDeltaPts;
    private BigDecimal previousGrossVolume;
    private BigDecimal previousEstimatedMargin;
    private Double previousConversionRate;
    private Double previousAvgMarginPercentage;

    /** Ranking por margem líquida % (não só volume/frequência). */
    private List<DestinationStat> destinationsByMargin;

    /** Leaderboard da equipe — só preenchido para OWNER com >1 membro. */
    private boolean showTeamLeaderboard;
    private List<ConsultantStat> teamLeaderboard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationStat {
        private String cityOrName;
        private long count;
        private BigDecimal volume;
        private BigDecimal margin;
        /** Margem líquida % sobre o custo base. */
        private Double marginPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultantStat {
        private UUID consultantId;
        private String consultantName;
        private long proposalsHandled;
        private long proposalsWon;
        private double conversionRate;
        private BigDecimal volume;
        private BigDecimal margin;
        private Double marginPercentage;
    }
}
