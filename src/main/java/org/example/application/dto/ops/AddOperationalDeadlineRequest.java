package org.example.application.dto.ops;

import lombok.Data;
import org.example.domain.enums.OperationalAlertLevel;
import org.example.domain.enums.OperationalDeadlineType;

import java.time.Instant;

@Data
public class AddOperationalDeadlineRequest {
    private OperationalDeadlineType deadlineType;
    private String title;
    private Instant dueAt;
    private OperationalAlertLevel alertLevel;
}
