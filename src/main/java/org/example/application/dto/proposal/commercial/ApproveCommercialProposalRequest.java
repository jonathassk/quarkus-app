package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveCommercialProposalRequest {
    private String name;
    private String email;
    private UUID optionId;
    private List<UUID> addonIds;
    private String termsText;
    private String sessionId;
}
