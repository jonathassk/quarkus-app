package org.example.application.dto.ops;

import lombok.Data;
import org.example.domain.enums.OperationalNextAction;
import org.example.domain.enums.OperationalServiceStatus;

import java.time.Instant;
import java.util.Map;

@Data
public class UpdateOperationalServiceStatusRequest {
    private OperationalServiceStatus status;
    private OperationalNextAction nextAction;
    private String nextActionLabel;
    private Instant nextActionDueAt;
    private String internalNotes;
    private Map<String, Object> details;
    private Map<String, Object> publicInfo;
}
