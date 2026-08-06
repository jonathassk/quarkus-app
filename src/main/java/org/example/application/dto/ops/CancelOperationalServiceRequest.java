package org.example.application.dto.ops;

import lombok.Data;

@Data
public class CancelOperationalServiceRequest {
    private String reason;
    private String cancellationPolicy;
    private Long estimatedPenaltyMinor;
    private Long supplierCreditMinor;
}
