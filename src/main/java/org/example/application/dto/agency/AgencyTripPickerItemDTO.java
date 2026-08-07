package org.example.application.dto.agency;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyTripPickerItemDTO {
    private UUID id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverImageUrl;
    private String createdByName;
    /** AGENT or TRAVELER */
    private String origin;
    private int segmentCount;
    private String proposalStatus;
}
