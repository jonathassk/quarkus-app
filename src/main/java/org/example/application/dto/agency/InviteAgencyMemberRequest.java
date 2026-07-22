package org.example.application.dto.agency;

import lombok.*;
import org.example.domain.enums.AgencyRole;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteAgencyMemberRequest {
    private String email;
    private AgencyRole agencyRole;
}
