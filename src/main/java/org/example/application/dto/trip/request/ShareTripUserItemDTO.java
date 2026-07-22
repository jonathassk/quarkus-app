package org.example.application.dto.trip.request;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTripUserItemDTO {
    private UUID userId;
    private String email;
    /** ADMIN (edit) or VIEWER */
    private String permission;
}
