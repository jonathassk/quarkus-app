package org.example.application.services.proposal.pricing;

import org.example.domain.enums.ItemPricingMode;
import org.example.domain.enums.MarkupKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingEngineTest {

    @Test
    void costPlus_markupPercentAndFee() {
        // Custo 800000 (R$ 8.000), markup 25% = 200000, taxa 30000 → cliente 1.030.000
        var result = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COST_PLUS,
                800_000L,
                MarkupKind.PERCENT,
                null,
                2500,
                null, null, null, null,
                30_000L,
                null));
        assertEquals(800_000L, result.costMinor());
        assertEquals(200_000L, result.markupAmountMinor());
        assertEquals(30_000L, result.serviceFeeMinor());
        assertEquals(1_030_000L, result.clientPriceMinor());
        assertEquals(230_000L, result.expectedRevenueMinor());
        assertEquals(2500, result.markupBps());
        // margem = 230000/1030000 ≈ 22.33% → 2233 bps
        assertEquals(2233, result.marginBps());
    }

    @Test
    void costPlus_exampleFromSpec_markup25_margin20() {
        // Custo 8000, preço 10000 → markup 25%, margem 20%
        var result = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COST_PLUS,
                800_000L,
                MarkupKind.PERCENT,
                null,
                2500,
                null, null, null, null,
                0L,
                null));
        assertEquals(1_000_000L, result.clientPriceMinor());
        assertEquals(2500, result.markupBps());
        assertEquals(2000, result.marginBps());
    }

    @Test
    void commission_mode() {
        // Fornecedor 600000, comissão 10%, taxa 20000 → cliente 620000, receita 80000
        var result = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COMMISSION,
                null,
                null, null, null,
                600_000L,
                MarkupKind.PERCENT,
                null,
                1000,
                20_000L,
                null));
        assertEquals(60_000L, result.commissionMinor());
        assertEquals(620_000L, result.clientPriceMinor());
        assertEquals(80_000L, result.expectedRevenueMinor());
        assertEquals(540_000L, result.costMinor()); // 600k - 60k
    }

    @Test
    void manual_withoutCost_marginNull() {
        var result = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.MANUAL,
                null,
                null, null, null,
                null, null, null, null,
                0L,
                500_000L));
        assertEquals(500_000L, result.clientPriceMinor());
        assertNull(result.marginBps());
        assertNull(result.markupBps());
        assertEquals(0L, result.expectedRevenueMinor());
    }

    @Test
    void manual_withCost() {
        var result = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.MANUAL,
                400_000L,
                null, null, null,
                null, null, null, null,
                0L,
                500_000L));
        assertEquals(500_000L, result.clientPriceMinor());
        assertEquals(100_000L, result.expectedRevenueMinor());
        assertEquals(2000, result.marginBps());
    }

    @Test
    void sumItems_andAdjustment() {
        var a = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COST_PLUS, 100_000L, MarkupKind.FIXED, 20_000L, null,
                null, null, null, null, 0L, null));
        var b = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COST_PLUS, 50_000L, MarkupKind.FIXED, 10_000L, null,
                null, null, null, null, 5_000L, null));
        var totals = PricingEngine.sumItems(List.of(a, b));
        assertEquals(150_000L, totals.supplierCostMinor());
        assertEquals(185_000L, totals.clientPriceMinor());
        var discounted = PricingEngine.applyAdjustment(totals, -30_000L);
        assertEquals(155_000L, discounted.clientPriceMinor());
    }

    @Test
    void toMinor_and_toMajor() {
        assertEquals(12345L, PricingEngine.toMinor(new java.math.BigDecimal("123.45")));
        assertEquals(new java.math.BigDecimal("123.45"), PricingEngine.toMajor(12345L));
    }

    @Test
    void fx_conversion_withProtection() {
        // €1000.00 * 6.20 = R$ 6200; +3% = 6386
        long converted = PricingEngine.convertForeignCost(100_000L, 6_200_000L, 300);
        assertEquals(638_600L, converted);
        var result = PricingEngine.priceItem(new PricingEngine.ItemInput(
                ItemPricingMode.COST_PLUS,
                null,
                MarkupKind.PERCENT,
                null,
                0,
                null, null, null, null,
                0L,
                null,
                100_000L,
                6_200_000L,
                300));
        assertEquals(638_600L, result.costMinor());
        assertEquals(638_600L, result.clientPriceMinor());
    }
}
