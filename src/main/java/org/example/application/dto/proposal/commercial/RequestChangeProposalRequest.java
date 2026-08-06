package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestChangeProposalRequest {
    private String name;
    private String email;
    private List<String> types;
    private String message;
}
