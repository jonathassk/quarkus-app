package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.EventParticipantStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RsvpRequestDTO {
    private EventParticipantStatus status;
}
