package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyOpportunityFileDTO {
    private UUID id;
    private UUID opportunityId;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private String kind;
    private UUID uploadedByUserId;
    private String uploadedByName;
    private Instant createdAt;
    private String viewUrl;
}
