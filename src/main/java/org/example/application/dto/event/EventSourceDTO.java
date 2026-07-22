package org.example.application.dto.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSourceDTO {
    private UUID tripId;
    private Integer segmentIndex;
    private String activityId;
}
