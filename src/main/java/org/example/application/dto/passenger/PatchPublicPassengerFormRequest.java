package org.example.application.dto.passenger;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PatchPublicPassengerFormRequest {
    private String displayName;
    private String passengerType;
    private String email;
    private String whatsapp;
    private String nationality;
    private LocalDate birthDate;
    private String documentType;
    private String documentNumber;
    private LocalDate documentExpiresAt;
    private String formPayload;
}
