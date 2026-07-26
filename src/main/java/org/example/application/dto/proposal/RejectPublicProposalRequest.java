package org.example.application.dto.proposal;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectPublicProposalRequest {
    private String reason;
    private String name;
    private String email;
}
