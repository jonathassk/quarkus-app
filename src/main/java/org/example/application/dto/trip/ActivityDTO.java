package org.example.application.dto.trip;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {
    private String name;
    private String activityType;
    private Instant startTime;
    private Instant endTime;
    private LocalDate date;
    private String address;
    /** Latitude WGS84 (ex.: 48.8606) para mapas */
    private Double latitude;
    /** Longitude WGS84 (ex.: 2.3376) para mapas */
    private Double longitude;
    private BigDecimal cost;
    private String notes;
}
