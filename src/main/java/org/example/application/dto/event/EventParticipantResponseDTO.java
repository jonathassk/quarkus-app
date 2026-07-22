package org.example.application.dto.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantResponseDTO {
    private UUID userId;
    private String fullName;
    private String profilePictureUrl;
    private EventParticipantRole role;
    private EventParticipantStatus status;
}
