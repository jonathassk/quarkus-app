package org.example.application.dto.ops;

import lombok.Data;

@Data
public class CreateServiceChangeRequest {
    private String requestNote;
    private Long priceDeltaMinor;
}
