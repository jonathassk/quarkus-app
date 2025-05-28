package org.example.application.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
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
    private Instant startDate;
    private Instant endDate;
    private String coverImageUrl;
    private String visibility;
    private List<TripSegmentRequestDTO> segments;
    private List<TripUserRequestDTO> users;
}
