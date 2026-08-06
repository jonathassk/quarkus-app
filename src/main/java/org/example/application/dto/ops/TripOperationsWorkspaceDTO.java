package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.application.dto.passenger.TripPassengerResponse;
import org.example.domain.enums.OperationalNextAction;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.ProposalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOperationsWorkspaceDTO {
    private UUID tripId;
    private String tripName;
    private String clientName;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer daysUntilDeparture;
    private UUID consultantId;
    private String consultantName;
    private ProposalStatus proposalStatus;
    private OperationStatus operationStatus;
    private int readinessPercent;
    private List<ReadinessCheckDTO> readinessChecks;
    private List<OperationalAlertDTO> alerts;
    private List<OperationalServiceDTO> services;
    private List<TripPassengerResponse> passengers;
    private List<OperationalDeadlineDTO> deadlines;
    private List<OperationalPendingDTO> pendencies;
    private List<OperationalDocumentDTO> documents;
    private List<ServiceChangeRequestDTO> changeRequests;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadinessCheckDTO {
        private String code;
        private String label;
        private boolean done;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalAlertDTO {
        private String level;
        private String message;
        private UUID serviceId;
        private Instant dueAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalPendingDTO {
        private UUID serviceId;
        private String serviceName;
        private String reason;
        private OperationalNextAction nextAction;
        private Instant dueAt;
    }
}
