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
public class MealRequestDTO {
    private String name;
    private String mealType; // breakfast, lunch, dinner, snack
    private String description;
    private String restaurantName;
    private String address;
    private Instant startTime;
    private Instant endTime;
    private LocalDate date;
    private BigDecimal cost;
    private String notes;
}
