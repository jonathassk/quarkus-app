package org.example.application.dto.agency;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyBrandingDTO {
    private UUID id;
    private String name;
    private String slug;
    private String logoUrl;
    private String primaryColor;
    private String whatsappNumber;
    private BigDecimal markupPercentage;
    private String planType;
    private String agencyRole;
}
