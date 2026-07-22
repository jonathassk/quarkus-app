package org.example.application.dto.agency;

import lombok.*;
import org.example.domain.enums.B2bTripLogAction;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2bAuditLogDTO {
    private UUID id;
    private UUID tripId;
    private UUID actorUserId;
    private String actorEmail;
    private B2bTripLogAction action;
    private String entityType;
    private UUID entityId;
    private String description;
    private Instant createdAt;
}
