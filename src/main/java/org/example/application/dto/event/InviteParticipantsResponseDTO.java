package org.example.application.dto.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteParticipantsResponseDTO {
    private List<InvitedUserDTO> invited;
    private List<UUID> skipped;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvitedUserDTO {
        private UUID userId;
        private String fullName;
        private EventParticipantStatus status;
        private EventParticipantRole role;
    }
}
