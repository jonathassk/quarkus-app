package org.example.application.dto.agency;

import lombok.*;
import org.example.domain.enums.AgencyRole;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyMemberDTO {
    private UUID id;
    private UUID userId;
    private String email;
    private String fullName;
    private AgencyRole agencyRole;
    private Instant createdAt;
}
