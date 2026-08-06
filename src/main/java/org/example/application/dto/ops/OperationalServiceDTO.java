package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.OperationalNextAction;
import org.example.domain.enums.OperationalServiceStatus;
import org.example.domain.enums.OperationalServiceType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalServiceDTO {
    private UUID id;
    private UUID proposalItemId;
    private OperationalServiceType serviceType;
    private String name;
    private String subtitle;
    private String supplierName;
    private UUID supplierId;
    private OperationalServiceStatus status;
    private OperationalNextAction nextAction;
    private String nextActionLabel;
    private Instant nextActionDueAt;
    private Map<String, Object> details;
    private Map<String, Object> publicInfo;
    private String internalNotes;
    private Long costEstimatedMinor;
    private Long priceApprovedMinor;
    private Long confirmedCostMinor;
    private Long costDivergenceMinor;
    private boolean costDivergence;
    private String currency;
    private String locator;
    private String ticketNumber;
    private Instant confirmedAt;
    private String cancellationPolicy;
    private boolean published;
    private Integer quantity;
    private LocalDate startDate;
    private LocalDate endDate;
    private int sortOrder;
    private List<UUID> passengerIds;
    private UUID voucherDocumentId;
    private List<OperationalDocumentDTO> documents;
    private String cancelReason;
    private Instant cancelledAt;
    private Long estimatedPenaltyMinor;
    private Long supplierCreditMinor;
}
