package org.example.application.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRequestDTO {
    private String name;
    private String activityType;
    private Instant startTime;
    private Instant endTime;
    private String address;
    private BigDecimal cost;
    private String notes;
}
