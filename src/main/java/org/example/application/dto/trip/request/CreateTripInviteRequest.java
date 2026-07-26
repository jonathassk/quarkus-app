package org.example.application.dto.trip.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripInviteRequest {
    private String email;
    /** ADMIN ou VIEWER. Default VIEWER. */
    private String permission;
}
