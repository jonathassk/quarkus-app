package org.example.application.dto.proposal;

import lombok.*;

import java.math.BigDecimal;

/**
 * Linha do breakdown de custo base da proposta B2B.
 * Códigos fixos: FLIGHT, HOTEL, INSURANCE, TOURS; extras usam CUSTOM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseCostItemDTO {
    private String id;
    /** FLIGHT | HOTEL | INSURANCE | TOURS | CUSTOM */
    private String code;
    private String label;
    private BigDecimal amount;
}
