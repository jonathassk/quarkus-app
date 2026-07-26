package org.example.application.dto.proposal;

import lombok.*;

import java.util.List;

/**
 * Aceite digital na página pública — identifica o cliente e o(s) tier(s) escolhido(s).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePublicProposalRequest {
    private String name;
    private String email;
    /** Códigos dos tiers selecionados (opcional). */
    private List<String> tierCodes;
}
