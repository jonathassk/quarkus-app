package org.example.application.dto.trip.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTripEmailRequestDTO {
    private String toEmail;
    private String subject;
    private String textBody;
    private String htmlBody;
    private String personalMessage;
}
