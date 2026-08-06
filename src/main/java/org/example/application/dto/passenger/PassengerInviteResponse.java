package org.example.application.dto.passenger;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PassengerInviteResponse {
    private String inviteToken;
    private String inviteUrl;
    private String emailSentTo;
}
