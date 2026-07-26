package org.example.application.dto.trip.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.TripShareLinkScope;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripShareLinkDTO {
    private UUID id;
    private UUID tripId;
    private String code;
    private TripShareLinkScope scope;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;
    private String url;
    private boolean active;
}
