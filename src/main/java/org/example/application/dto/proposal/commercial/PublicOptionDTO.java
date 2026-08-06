package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicOptionDTO {
    private UUID id;
    private String name;
    private String subtitle;
    private String shortDescription;
    private String coverImageUrl;
    private boolean recommended;
    private List<String> includes;
    private List<String> excludes;
    private String paymentConditions;
    private long clientPriceMinor;
    private List<PublicItemDTO> items;
}
