package org.example.application.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSegmentRequestDTO {
    private String cityId;
    private Instant arrivalDate;
    private Instant departureDate;
    private String notes;
    private Map<LocalDate, DailyActivitiesRequestDTO> dailyActivities;
}
