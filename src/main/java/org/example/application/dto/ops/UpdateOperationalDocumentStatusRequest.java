package org.example.application.dto.ops;

import lombok.Data;
import org.example.domain.enums.OperationalDocumentStatus;

@Data
public class UpdateOperationalDocumentStatusRequest {
    private OperationalDocumentStatus status;
}
