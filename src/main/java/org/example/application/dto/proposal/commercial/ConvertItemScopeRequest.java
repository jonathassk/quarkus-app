package org.example.application.dto.proposal.commercial;

import lombok.*;
import org.example.domain.enums.ProposalItemScope;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertItemScopeRequest {
    private ProposalItemScope targetScope;
    private UUID targetOptionId;
}
