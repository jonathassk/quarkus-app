package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.TripPaymentKind;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalCheckoutRequest {
    /** DEPOSIT (sinal) ou FULL (valor cheio). Default: DEPOSIT se houver preço. */
    private TripPaymentKind kind;
    private String successUrl;
    private String cancelUrl;
}
