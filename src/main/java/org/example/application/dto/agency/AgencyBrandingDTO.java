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
    /** true em planos pagos B2B: proposta usa logo/cores da agência. */
    private Boolean whiteLabelEnabled;
    /** true no Essencial: proposta white-label exibe “Powered by Baggagi”. */
    private Boolean poweredByBaggagi;
    private String contactEmail;
    private String agentTitle;
    private String agentPhotoUrl;
    private String websiteOrInstagram;
    private String pricingModel;
    private Integer minMarginBps;
    private Long minServiceFeeMinor;
    private Boolean allowBelowMinimum;
    private Boolean requireDiscountReason;
    private Integer maxDiscountBps;
    private String onboardingStep;
    private Boolean onboardingCompleted;
    private Boolean demoDataActive;
}
