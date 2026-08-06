package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.PricingEditMode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetPricingModeRequest {
    private PricingEditMode pricingEditMode;
}
