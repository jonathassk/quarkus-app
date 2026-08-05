package org.example.application.dto.agency;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyDemoSeedResponse {
    private UUID clientId;
    private UUID tripId;
    private String shareCode;
    private String clientName;
    private String tripName;
}
