package org.example.application.services.proposal.pricing;

import org.example.domain.enums.ItemPricingMode;
import org.example.domain.enums.MarkupKind;

/**
 * Motor puro de precificação — cálculos oficiais em minor units.
 * Não inventa margem quando o custo está ausente.
 */
public final class PricingEngine {

    private PricingEngine() {}

    public record ItemInput(
            ItemPricingMode mode,
            Long costMinor,
            MarkupKind markupKind,
            Long markupValueMinor,
            Integer markupPercentBps,
            Long supplierPublicPriceMinor,
            MarkupKind commissionKind,
            Long commissionValueMinor,
            Integer commissionPercentBps,
            long serviceFeeMinor,
            Long manualClientPriceMinor,
            /** Custo na moeda original (minor). Se setado com fxRateMicros, converte. */
            Long foreignCostMinor,
            /** Taxa: quantos micros da moeda de apresentação por 1 unidade da moeda estrangeira (1.0 = 1_000_000). */
            Long fxRateMicros,
            /** Proteção cambial em bps aplicada sobre o custo convertido. */
            Integer fxProtectionBps
    ) {
        public ItemInput(
                ItemPricingMode mode,
                Long costMinor,
                MarkupKind markupKind,
                Long markupValueMinor,
                Integer markupPercentBps,
                Long supplierPublicPriceMinor,
                MarkupKind commissionKind,
                Long commissionValueMinor,
                Integer commissionPercentBps,
                long serviceFeeMinor,
                Long manualClientPriceMinor) {
            this(mode, costMinor, markupKind, markupValueMinor, markupPercentBps,
                    supplierPublicPriceMinor, commissionKind, commissionValueMinor, commissionPercentBps,
                    serviceFeeMinor, manualClientPriceMinor, null, null, null);
        }
    }

    public record ItemResult(
            long costMinor,
            long markupAmountMinor,
            long serviceFeeMinor,
            long commissionMinor,
            long clientPriceMinor,
            long expectedRevenueMinor,
            Integer marginBps,
            Integer markupBps
    ) {}

    public record OptionTotals(
            long supplierCostMinor,
            long markupAmountMinor,
            long serviceFeeMinor,
            long commissionMinor,
            long clientPriceMinor,
            long expectedRevenueMinor,
            Integer marginBps,
            Integer markupBps
    ) {}

    /** Converte custo estrangeiro → minor da moeda de apresentação, com proteção. */
    public static long convertForeignCost(
            long foreignCostMinor, long fxRateMicros, Integer fxProtectionBps) {
        if (foreignCostMinor <= 0 || fxRateMicros <= 0) {
            return 0;
        }
        // foreignMajor * rate = presentationMajor; both in minor → foreign * rateMicros / 1_000_000
        long converted = Math.round(foreignCostMinor * (fxRateMicros / 1_000_000.0));
        if (fxProtectionBps != null && fxProtectionBps > 0) {
            converted = Math.round(converted * (1.0 + fxProtectionBps / 10_000.0));
        }
        return Math.max(0, converted);
    }

    public static ItemResult priceItem(ItemInput in) {
        long resolvedCost = nz(in.costMinor());
        if ((resolvedCost <= 0 || in.costMinor() == null)
                && in.foreignCostMinor() != null
                && in.fxRateMicros() != null
                && in.fxRateMicros() > 0) {
            resolvedCost = convertForeignCost(
                    in.foreignCostMinor(), in.fxRateMicros(), in.fxProtectionBps());
        }
        ItemInput effective = new ItemInput(
                in.mode(),
                resolvedCost > 0 ? Long.valueOf(resolvedCost) : in.costMinor(),
                in.markupKind(),
                in.markupValueMinor(),
                in.markupPercentBps(),
                in.supplierPublicPriceMinor(),
                in.commissionKind(),
                in.commissionValueMinor(),
                in.commissionPercentBps(),
                in.serviceFeeMinor(),
                in.manualClientPriceMinor(),
                in.foreignCostMinor(),
                in.fxRateMicros(),
                in.fxProtectionBps());
        long fee = Math.max(0, effective.serviceFeeMinor());
        return switch (effective.mode() == null ? ItemPricingMode.COST_PLUS : effective.mode()) {
            case COST_PLUS -> priceCostPlus(effective, fee);
            case COMMISSION -> priceCommission(effective, fee);
            case MANUAL -> priceManual(effective, fee);
        };
    }

    private static ItemResult priceCostPlus(ItemInput in, long fee) {
        long cost = nz(in.costMinor());
        long markupAmount = resolvePercentOrFixed(
                cost,
                in.markupKind() != null ? in.markupKind() : MarkupKind.PERCENT,
                in.markupValueMinor(),
                in.markupPercentBps());
        long client = cost + markupAmount + fee;
        long revenue = client - cost;
        return new ItemResult(
                cost,
                markupAmount,
                fee,
                0,
                client,
                revenue,
                marginBps(client, cost),
                markupBps(cost, markupAmount));
    }

    private static ItemResult priceCommission(ItemInput in, long fee) {
        long publicPrice = nz(in.supplierPublicPriceMinor());
        long commission = resolvePercentOrFixed(
                publicPrice,
                in.commissionKind() != null ? in.commissionKind() : MarkupKind.PERCENT,
                in.commissionValueMinor(),
                in.commissionPercentBps());
        long client = publicPrice + fee;
        long revenue = commission + fee;
        // Custo efetivo para a agência = preço público − comissão (o que fica com o fornecedor)
        long effectiveCost = publicPrice - commission;
        return new ItemResult(
                effectiveCost,
                0,
                fee,
                commission,
                client,
                revenue,
                marginBps(client, effectiveCost),
                null);
    }

    private static ItemResult priceManual(ItemInput in, long fee) {
        long client = nz(in.manualClientPriceMinor()) + fee;
        // Se service fee já estiver embutida no preço manual, preferimos não somar de novo —
        // o caller deve passar fee=0 quando o preço final já é o cobrado.
        if (in.manualClientPriceMinor() != null && fee > 0
                && in.manualClientPriceMinor() >= fee) {
            // Interpretação: manualClientPriceMinor é o preço final desejado (já inclui taxa).
            client = in.manualClientPriceMinor();
            fee = 0;
        }
        Long costOpt = in.costMinor();
        if (costOpt == null) {
            return new ItemResult(0, 0, fee, 0, client, 0, null, null);
        }
        long cost = Math.max(0, costOpt);
        long revenue = client - cost;
        long markupAmount = Math.max(0, client - cost - fee);
        return new ItemResult(
                cost,
                markupAmount,
                fee,
                0,
                client,
                revenue,
                marginBps(client, cost),
                markupBps(cost, markupAmount));
    }

    public static OptionTotals sumItems(Iterable<ItemResult> items) {
        long cost = 0;
        long markup = 0;
        long fee = 0;
        long commission = 0;
        long client = 0;
        long revenue = 0;
        boolean anyMissingMargin = false;
        for (ItemResult r : items) {
            cost += r.costMinor();
            markup += r.markupAmountMinor();
            fee += r.serviceFeeMinor();
            commission += r.commissionMinor();
            client += r.clientPriceMinor();
            revenue += r.expectedRevenueMinor();
            if (r.marginBps() == null) {
                anyMissingMargin = true;
            }
        }
        return new OptionTotals(
                cost,
                markup,
                fee,
                commission,
                client,
                revenue,
                anyMissingMargin && cost == 0 ? null : marginBps(client, cost),
                markupBps(cost, markup));
    }

    public static OptionTotals applyAdjustment(OptionTotals base, long adjustmentMinor) {
        long client = Math.max(0, base.clientPriceMinor() + adjustmentMinor);
        long revenue = client - base.supplierCostMinor();
        return new OptionTotals(
                base.supplierCostMinor(),
                base.markupAmountMinor(),
                base.serviceFeeMinor(),
                base.commissionMinor(),
                client,
                revenue,
                marginBps(client, base.supplierCostMinor()),
                base.markupBps());
    }

    public static long resolvePercentOrFixed(
            long base,
            MarkupKind kind,
            Long fixedMinor,
            Integer percentBps) {
        if (kind == MarkupKind.FIXED) {
            return Math.max(0, nz(fixedMinor));
        }
        if (percentBps == null || percentBps == 0 || base == 0) {
            return 0;
        }
        // bps: 2500 = 25%
        return Math.round(base * (percentBps / 10_000.0));
    }

    /** Margem sobre a venda = (preço − custo) / preço. */
    public static Integer marginBps(long clientPrice, long cost) {
        if (clientPrice <= 0) {
            return null;
        }
        long result = clientPrice - cost;
        return (int) Math.round(result * 10_000.0 / clientPrice);
    }

    /** Acréscimo sobre o custo = (preço − custo) / custo — aqui usamos markupAmount/cost. */
    public static Integer markupBps(long cost, long markupAmount) {
        if (cost <= 0) {
            return null;
        }
        return (int) Math.round(markupAmount * 10_000.0 / cost);
    }

    public static long toMinor(java.math.BigDecimal major) {
        if (major == null) {
            return 0;
        }
        return major.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    public static java.math.BigDecimal toMajor(long minor) {
        return java.math.BigDecimal.valueOf(minor, 2);
    }

    private static long nz(Long v) {
        return v == null ? 0 : Math.max(0, v);
    }
}
