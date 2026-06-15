package org.example.application.dto.trip;

import lombok.*;

import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbProperty;
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
    @JsonbProperty(nillable = true)
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate arrivalDate;
    @JsonbProperty(nillable = true)
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate departureDate;
    private int startDay;
    private int endDay;
    private String notes;
    private BigDecimal dailyCost;
    private List<MealDTO> meals;
    private List<ActivityDTO> activities;
}
