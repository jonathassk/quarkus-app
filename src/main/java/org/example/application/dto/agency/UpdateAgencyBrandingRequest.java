package org.example.application.dto.agency;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgencyBrandingRequest {
    private String name;
    private String primaryColor;
    private String whatsappNumber;
    private BigDecimal markupPercentage;
    private String contactEmail;
    private String agentTitle;
    private String agentPhotoUrl;
    private String websiteOrInstagram;
    private String pricingModel;
}
