package org.example.application.dto.payment.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {
    private String paymentType; // MENSAL, ANUAL, MENSAL_TRIP_AGENT, ANUAL_TRIP_AGENT, UNITARIO
    private Long targetId;      // Workspace ID (for subscription) or Trip ID (for unitario)
}
