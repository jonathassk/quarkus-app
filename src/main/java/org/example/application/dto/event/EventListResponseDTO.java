package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventListResponseDTO {
    private List<EventResponseDTO> organizing;
    private List<EventResponseDTO> participating;
    private List<EventResponseDTO> pendingInvites;
}
