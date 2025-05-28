package org.example.application.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyActivitiesRequestDTO {
    private List<ActivityRequestDTO> activities;
    private List<MealRequestDTO> meals;
}
