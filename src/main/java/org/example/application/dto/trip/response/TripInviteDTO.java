package org.example.application.dto.trip.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.TripInviteStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripInviteDTO {
    private UUID id;
    private UUID tripId;
    private String email;
    private String permissionLevel;
    private TripInviteStatus status;
    private Instant expiresAt;
    private Instant acceptedAt;
    private Instant createdAt;
    private String inviteUrl;
}
