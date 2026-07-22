package org.example.application.dto.trip;

import lombok.*;

import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {
    private String name;
    private String activityType;
    private LocalTime startTime;
    private LocalTime endTime;
    @JsonbProperty(nillable = true)
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate date;
    private int dayNumber;
    private String address;
    /** Latitude WGS84 (ex.: 48.8606) para mapas */
    private Double latitude;
    /** Longitude WGS84 (ex.: 2.3376) para mapas */
    private Double longitude;
    private BigDecimal cost;
    @Builder.Default
    private boolean optional = false;
    private String notes;
}
