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
}
