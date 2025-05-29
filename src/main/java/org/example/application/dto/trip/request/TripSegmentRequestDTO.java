package org.example.application.dto.trip.request;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSegmentRequestDTO {
    private String cityId;
    private LocalDate arrivalDate;
    private LocalDate departureDate;
    private String notes;
    private List<MealRequestDTO> meals;
    private List<ActivityRequestDTO> activities;
}
