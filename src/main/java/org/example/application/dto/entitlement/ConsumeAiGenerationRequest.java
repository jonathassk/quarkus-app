package org.example.application.dto.entitlement;

import lombok.*;
import org.example.domain.enums.AiGenerationKind;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeAiGenerationRequest {
    private UUID tripId;
    private AiGenerationKind kind;
}
