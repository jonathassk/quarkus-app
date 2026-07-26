package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.TripPaymentKind;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalCheckoutResponse {
    private String checkoutUrl;
    private UUID paymentId;
    private TripPaymentKind kind;
    private BigDecimal amount;
    private String currency;
}
