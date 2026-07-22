package org.example.application.dto.trip.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTripEmailResponseDTO {
    /** sent | queued | mailto_fallback */
    private String status;
    private String message;
}
