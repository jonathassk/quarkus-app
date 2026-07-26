package org.example.application.dto.agency;

import lombok.*;

import java.util.List;

/**
 * Resposta unificada do convite: membro ativo ou convite pendente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteAgencyMemberResponse {
    /** ACTIVE quando o usuário já existia; PENDING quando aguarda cadastro/aceitação. */
    private String status;
    private AgencyMemberDTO member;
    private AgencyInviteDTO invite;
}
