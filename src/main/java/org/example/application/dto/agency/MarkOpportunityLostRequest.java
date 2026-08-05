package org.example.application.dto.agency;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkOpportunityLostRequest {
    /** Código estruturado (PRICE, NO_RESPONSE, …). */
    private String lostReasonCode;
    /** Texto legível; se ausente, usa o label do código. */
    private String lostReason;
    private String lostCompetitor;
    private String lostNote;
    private Boolean lostMayReactivate;
    private java.time.LocalDate lostReactivateAt;
}
