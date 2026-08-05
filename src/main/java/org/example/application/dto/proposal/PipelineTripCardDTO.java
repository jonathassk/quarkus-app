package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.OperationStatus;
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
    private boolean allowNegotiation;
    private OperationStatus operationStatus;
    /** Badge financeiro derivado: NOT_REQUESTED | PENDING | PARTIAL | PAID | NONE. */
    private String paymentBadge;
    private BigDecimal baseCost;
    private BigDecimal finalPrice;
    /** finalPrice − baseCost */
    private BigDecimal margin;
    /** Markup % efetivo: (margin / baseCost) × 100 */
    private BigDecimal markupPercentage;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant lastContactAt;
    private Instant updatedAt;
    private UUID createdBy;
    private String createdByName;
    private UUID assignedConsultantId;
    private String assignedConsultantName;
    private UUID clientId;
    private String clientName;
    private String clientPhone;
    private Instant proposalLastViewedAt;
    private Integer proposalViewCount;
    private Integer proposalViewsToday;
}
