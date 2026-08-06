package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.AdjustmentType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialProposalAdjustmentDTO {
    private UUID id;
    private UUID optionId;
    private AdjustmentType adjustmentType;
    private long amountMinor;
    private Integer percentBps;
    private String reason;
    private Long previousClientPriceMinor;
    private UUID createdBy;
    private Instant createdAt;
}
