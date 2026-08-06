package org.example.application.dto.proposal;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Aceite digital na página pública — identifica o cliente, a opção e adicionais.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePublicProposalRequest {
    private String name;
    private String email;
    /** Códigos dos tiers selecionados (legado). */
    private List<String> tierCodes;
    /** Opção escolhida (motor multiopção). */
    private UUID optionId;
    private List<UUID> addonIds;
    private String termsText;
    private String sessionId;
}
