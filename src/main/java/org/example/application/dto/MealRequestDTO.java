package org.example.application.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealRequestDTO {
    private String name;
    private String mealType; // breakfast, lunch, dinner, snack
    private Instant time;
    private String restaurantName;
    private String address;
    private BigDecimal cost;
    private String notes;
}
