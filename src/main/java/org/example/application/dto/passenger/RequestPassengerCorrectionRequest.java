package org.example.application.dto.passenger;

import lombok.Data;

@Data
public class RequestPassengerCorrectionRequest {
    /** Campo: displayName, documentNumber, birthDate, nationality, email, whatsapp, documentExpiresAt, formPayload.* */
    private String fieldName;
    /** Valor que o agente acredita estar correto (opcional). */
    private String expectedValue;
    private String agentNote;
}
