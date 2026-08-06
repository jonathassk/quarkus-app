package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.PriceVisibility;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProposalSettingsRequest {
    private PriceVisibility priceVisibility;
    private String recommendationNote;
    /** Justificativa quando margem fica abaixo do mínimo e a política permite continuar. */
    private String belowMinimumJustification;
}
