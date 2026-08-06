package org.example.application.dto.passenger;

import lombok.Builder;
import lombok.Data;
import org.example.domain.enums.PassengerCorrectionStatus;

import java.util.UUID;

@Data
@Builder
public class PassengerCorrectionResponse {
    private UUID id;
    private UUID passengerId;
    private String fieldName;
    private String oldValue;
    private String expectedValue;
    private String correctedValue;
    private String agentNote;
    private PassengerCorrectionStatus status;
    private String requestedAt;
    private String resolvedAt;
}
