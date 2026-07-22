package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.ProposalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineTripCardDTO {
    private UUID tripId;
    private String name;
    private String shareCode;
    private ProposalStatus proposalStatus;
    private BigDecimal finalPrice;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant lastContactAt;
    private Instant updatedAt;
    private UUID createdBy;
    private String createdByName;
}
