package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLocationDTO {
    private String name;
    private String address;
    private String city;
    private String country;
    private Double latitude;
    private Double longitude;
}
