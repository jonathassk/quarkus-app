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
public class MealDTO {
    private String name;
    private String mealType; // breakfast, lunch, dinner, snack
    private String description;
    private String restaurantName;
    private String address;
    /** Latitude WGS84 para mapas */
    private Double latitude;
    /** Longitude WGS84 para mapas */
    private Double longitude;
    private LocalTime startTime;
    private LocalTime endTime;
    @JsonbProperty(nillable = true)
    @JsonbDateFormat("yyyy-MM-dd")
    private LocalDate date;
    private int dayNumber;
    private BigDecimal cost;
    private String notes;
}
