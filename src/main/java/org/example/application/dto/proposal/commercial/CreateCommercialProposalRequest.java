package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.ProposalFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommercialProposalRequest {
    /** SINGLE (default) or COMPARE. */
    private ProposalFormat format;
    /** Optional default markup bps when creating PACKAGE item (e.g. 1500 = 15%). */
    private Integer defaultMarkupPercentBps;
}
