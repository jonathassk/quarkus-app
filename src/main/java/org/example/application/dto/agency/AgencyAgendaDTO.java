package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyAgendaDTO {
    @Builder.Default
    private List<AgencyAgendaItemDTO> overdue = new ArrayList<>();
    @Builder.Default
    private List<AgencyAgendaItemDTO> today = new ArrayList<>();
    @Builder.Default
    private List<AgencyAgendaItemDTO> upcoming = new ArrayList<>();
    @Builder.Default
    private List<AgencyAgendaItemDTO> waiting = new ArrayList<>();
    @Builder.Default
    private List<AgencyAgendaItemDTO> missingNextAction = new ArrayList<>();
    private AgendaSummaryDTO summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgendaSummaryDTO {
        private int overdueCount;
        private int todayCount;
        private int upcomingCount;
        private int waitingCount;
        private int missingNextActionCount;
    }
}
