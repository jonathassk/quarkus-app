package org.example.application.dto.passenger;

import lombok.Builder;
import lombok.Data;
import org.example.domain.enums.PassengerFormStatus;
import org.example.domain.enums.PassengerType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PublicPassengerFormResponse {
    private UUID passengerId;
    private UUID tripId;
    private String tripName;
    private String displayName;
    private PassengerType passengerType;
    private PassengerFormStatus formStatus;
    private boolean primaryContact;
    private UUID guardianPassengerId;
    private String email;
    private String whatsapp;
    private String nationality;
    private LocalDate birthDate;
    private String documentType;
    private String documentNumber;
    private LocalDate documentExpiresAt;
    private String formPayload;
    private boolean isInternational;
    private boolean hasFlights;
    private boolean hasHotels;
    private boolean readOnly;
    private List<PassengerCorrectionResponse> openCorrections;
    /** Oferta de reuso: cliente CRM da mesma agência com consentimento prévio. */
    private boolean reusableProfileAvailable;
    private String reusableProfileName;
    /** Consentimento desta submissão para salvar no perfil da agência. */
    private Boolean saveToAgencyProfile;
}
