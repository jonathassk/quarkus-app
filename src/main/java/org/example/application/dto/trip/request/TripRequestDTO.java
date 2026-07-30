package org.example.application.dto.trip.request;

import java.util.UUID;

import lombok.*;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.TripUserDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TripRequestDTO {
    private String name;
    private String description;
    /** Passagens já compradas pelo usuário (texto livre). */
    private String flightDetails;
    /** Hospedagem já reservada pelo usuário (texto livre). */
    private String hotelDetails;
    private BigDecimal budgetTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private int durationDays;
    private Integer targetMonth;
    private String coverImageUrl;
    private UUID createdBy;
    private UUID workspaceId;
    private String visibility;
    private List<TripSegmentDTO> segments;
    private List<TripUserDTO> users;
}
