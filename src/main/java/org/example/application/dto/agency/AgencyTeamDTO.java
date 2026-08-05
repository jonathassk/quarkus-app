package org.example.application.dto.agency;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyTeamDTO {
    private List<AgencyMemberDTO> members;
    private List<AgencyInviteDTO> pendingInvites;
    /** Máximo de consultores do plano (0 = Solo/Essencial; 10 = Team). */
    private Integer maxConsultants;
    /** Consultores ativos (papel CONSULTANT). */
    private Integer consultantCount;
    /** true se ainda cabe convite / consultor no plano. */
    private Boolean canInvite;
}
