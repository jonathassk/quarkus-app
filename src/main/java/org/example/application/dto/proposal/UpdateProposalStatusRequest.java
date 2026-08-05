package org.example.application.dto.proposal;

import lombok.*;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.ProposalStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProposalStatusRequest {
    private ProposalStatus proposalStatus;
    /** Opcional: atualiza o subestado operacional (badge). */
    private OperationStatus operationStatus;
    /** Opcional: altera se a proposta aceita negociação. */
    private Boolean allowNegotiation;
}
