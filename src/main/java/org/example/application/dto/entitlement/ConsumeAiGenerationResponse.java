package org.example.application.dto.entitlement;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeAiGenerationResponse {
    private UUID generationId;
    private long used;
    private long limit;
    private boolean unlimited;
    /** FLASH | PRO — roteamento validado no servidor. */
    private String aiModelTier;
}
