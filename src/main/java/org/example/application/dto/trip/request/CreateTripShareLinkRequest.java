package org.example.application.dto.trip.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripShareLinkRequest {
    /** Opcional. Se omitido, o link não expira. */
    private Instant expiresAt;
}
