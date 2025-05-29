package org.example.application.dto.trip.request;

import lombok.*;

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
    private BigDecimal budgetTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverImageUrl;
    private String visibility;
    private List<TripSegmentRequestDTO> segments;
    private List<TripUserRequestDTO> users;
}
