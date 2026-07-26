package org.example.application.dto.payment.request;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {
    private String paymentType; // MENSAL, ANUAL, MENSAL_TRIP_AGENT(_STARTER|_TEAM), ANUAL_TRIP_AGENT(_STARTER|_TEAM), UNITARIO
    private UUID targetId;      // Workspace ID (for subscription) or Trip ID (for unitario)
    /** Optional; must match an allowed frontend origin (see CORS). */
    private String successUrl;
    /** Optional; must match an allowed frontend origin (see CORS). */
    private String cancelUrl;
}
