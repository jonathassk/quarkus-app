package org.example.application.dto.ops;

import lombok.Data;
import org.example.domain.enums.ServiceChangeStatus;

@Data
public class UpdateServiceChangeRequest {
    private ServiceChangeStatus status;
    private String requestNote;
    private Long priceDeltaMinor;
}
