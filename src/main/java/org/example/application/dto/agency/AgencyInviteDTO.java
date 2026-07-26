package org.example.application.dto.agency;

import lombok.*;
import org.example.domain.enums.AgencyInviteStatus;
import org.example.domain.enums.AgencyRole;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyInviteDTO {
    private UUID id;
    private String email;
    private AgencyRole agencyRole;
    private AgencyInviteStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private String invitedByEmail;
}
