package org.example.application.dto.trip.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.application.dto.trip.TripSegmentDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload reduzido da viagem pública — sem docs privados, membros ou pricing interno.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTripDTO {
    private String code;
    private UUID tripId;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private int durationDays;
    private String coverImageUrl;
    private String currency;
    private List<TripSegmentDTO> segments;
}
