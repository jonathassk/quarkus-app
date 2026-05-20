package org.example.application.dto.trip;

import lombok.*;

import java.math.BigDecimal;
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
    private BigDecimal dailyCost;
    private List<MealDTO> meals;
    private List<ActivityDTO> activities;
}
