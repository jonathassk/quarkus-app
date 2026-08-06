package org.example.application.dto.passenger;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateTripPassengerRequest {
    private String displayName;
    private String passengerType;
    private Boolean primaryContact;
    private UUID guardianPassengerId;
    private UUID sourceClientId;
    private String email;
    private String whatsapp;
    private String phone;
    private String nationality;
    private LocalDate birthDate;
}
