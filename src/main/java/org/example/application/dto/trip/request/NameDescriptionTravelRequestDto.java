package org.example.application.dto.trip.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class NameDescriptionTravelRequestDto {
    private String name;
    private String description;
}
