package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.ProposalStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalTierDTO {
    private UUID id;
    private String code;
    private String label;
    private BigDecimal priceDelta;
    private int sortOrder;
}
