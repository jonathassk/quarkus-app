package org.example.application.dto.proposal;

import lombok.*;
import org.example.application.dto.agency.AgencyBrandingDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.domain.enums.ProposalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload público da proposta white-label — sem markup %, audit ou docs internos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicProposalDTO {
    private String shareCode;
    private UUID tripId;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private int durationDays;
    private String coverImageUrl;
    private String currency;
    private BigDecimal finalPrice;
    private ProposalStatus proposalStatus;
    private AgencyBrandingDTO agency;
    private List<TripSegmentDTO> segments;
    private List<ProposalTierDTO> tiers;
    private List<PublicDocumentDTO> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicDocumentDTO {
        private UUID id;
        private String title;
        private String contentType;
        private UUID activityId;
        private UUID segmentId;
    }
}
