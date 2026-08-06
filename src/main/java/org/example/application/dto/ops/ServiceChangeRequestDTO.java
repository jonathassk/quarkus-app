package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.ServiceChangeStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceChangeRequestDTO {
    private UUID id;
    private UUID serviceId;
    private String serviceName;
    private ServiceChangeStatus status;
    private String requestNote;
    private Long priceDeltaMinor;
    private UUID requestedByUserId;
    private String requestedByName;
    private Instant resolvedAt;
    private Instant createdAt;
}
