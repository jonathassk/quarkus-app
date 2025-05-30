package org.example.application.dto.trip.request;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
    private LocalDate date;
    private String address;
    private BigDecimal cost;
    private String notes;
}
