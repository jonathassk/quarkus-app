package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.AdjustmentType;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdjustmentRequest {
    private UUID optionId;
    private AdjustmentType adjustmentType;
    private Long amountMinor;
    private Integer percentBps;
    private String reason;
    /** Justificativa se o desconto deixar a margem abaixo do mínimo. */
    private String belowMinimumJustification;
}
