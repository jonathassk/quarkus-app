package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.ProposalStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProposalStatusRequest {
    private ProposalStatus proposalStatus;
}
