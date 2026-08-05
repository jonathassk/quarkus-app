package org.example.application.dto.proposal;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProposalFollowUpRequest {
    /** null limpa o follow-up. */
    private Instant nextFollowUpAt;
}
