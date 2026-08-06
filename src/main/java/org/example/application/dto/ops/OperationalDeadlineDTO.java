package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.OperationalAlertLevel;
import org.example.domain.enums.OperationalDeadlineType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalDeadlineDTO {
    private UUID id;
    private UUID serviceId;
    private String serviceName;
    private OperationalDeadlineType deadlineType;
    private String title;
    private Instant dueAt;
    private OperationalAlertLevel alertLevel;
    private Instant completedAt;
    /** Nível calculado para UI (CRITICAL / WARNING / INFO). */
    private String computedAlertLevel;
}
