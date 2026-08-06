package org.example.domain.enums;

/**
 * Modo de precificação de um item comercial.
 */
public enum ItemPricingMode {
    /** Custo líquido + markup + taxa = preço do cliente. */
    COST_PLUS,
    /** Preço público do fornecedor + comissão + taxa. */
    COMMISSION,
    /** Preço final informado manualmente; custo opcional. */
    MANUAL;

    public static ItemPricingMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return COST_PLUS;
        }
        for (ItemPricingMode m : values()) {
            if (m.name().equalsIgnoreCase(value.trim())) {
                return m;
            }
        }
        throw new IllegalArgumentException("pricing_mode inválido: " + value);
    }
}
