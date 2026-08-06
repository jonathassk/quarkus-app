package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicItemDTO {
    private UUID id;
    private String name;
    private String subtitle;
    private String itemType;
    private Long clientPriceMinor;
    private boolean hidePrice;
    private boolean optional;
    private String supplierDisplay;
}
