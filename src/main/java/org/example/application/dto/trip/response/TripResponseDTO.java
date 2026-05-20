package org.example.application.dto.trip.response;

import lombok.*;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.TripUserDTO;
import org.example.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TripResponseDTO {
    private Long id;
    /** Derived from {@code startDate}/{@code endDate} and today (PLANNING / ONGOING / COMPLETED). */
    private TripStatus status;
    private String name;
    private String description;
    private BigDecimal budgetTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverImageUrl;
    private String visibility;
    private List<TripSegmentDTO> segments;
    private List<TripUserDTO> users;
}
