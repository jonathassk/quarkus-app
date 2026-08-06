package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetNextActionRequest {
    private String actionKind;
    private String title;
    private Instant dueAt;
    private UUID assigneeUserId;
    private String note;
    private String taskType;
    private String priority;
}
