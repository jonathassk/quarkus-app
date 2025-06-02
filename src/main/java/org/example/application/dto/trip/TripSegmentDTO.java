package org.example.application.dto.trip;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSegmentDTO {
    private String cityId;
    private LocalDate arrivalDate;
    private LocalDate departureDate;
    private String notes;
    private List<MealDTO> meals;
    private List<ActivityDTO> activities;
}
