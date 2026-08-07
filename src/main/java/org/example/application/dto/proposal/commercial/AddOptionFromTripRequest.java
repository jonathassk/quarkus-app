package org.example.application.dto.proposal.commercial;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOptionFromTripRequest {
    private UUID tripId;
    /** CLONE (default) or LINK */
    private String mode;
    private String name;
}
