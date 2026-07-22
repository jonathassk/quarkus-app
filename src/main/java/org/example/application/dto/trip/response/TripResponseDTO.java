package org.example.application.dto.trip.response;

import java.util.UUID;

import lombok.*;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.TripUserDTO;
import org.example.domain.enums.ProposalStatus;
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
    private UUID id;
    /** Derived from {@code startDate}/{@code endDate} and today (PLANNING / ONGOING / COMPLETED). */
    private TripStatus status;
    private String name;
    private String description;
    private BigDecimal budgetTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private int durationDays;
    private Integer targetMonth;
    private String coverImageUrl;
    private String visibility;
    private UUID workspaceId;
    private List<TripSegmentDTO> segments;
    private List<TripUserDTO> users;
    private UUID createdBy;
    private UUID agencyId;
    private ProposalStatus proposalStatus;
    private BigDecimal baseCost;
    private BigDecimal finalPrice;
    private String shareCode;
    private String currency;
}
