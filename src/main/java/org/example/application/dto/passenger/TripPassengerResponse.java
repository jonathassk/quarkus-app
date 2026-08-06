package org.example.application.dto.passenger;

import lombok.Builder;
import lombok.Data;
import org.example.domain.enums.PassengerDocReviewStatus;
import org.example.domain.enums.PassengerFormStatus;
import org.example.domain.enums.PassengerType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TripPassengerResponse {
    private UUID id;
    private UUID tripId;
    private String displayName;
    private PassengerType passengerType;
    private PassengerFormStatus formStatus;
    private boolean primaryContact;
    private UUID guardianPassengerId;
    private UUID sourceClientId;
    private String email;
    private String whatsapp;
    private String phone;
    private String nationality;
    private LocalDate birthDate;
    private String documentType;
    /** Número mascarado na listagem do agente (ex.: G•••••42). */
    private String documentNumberMasked;
    private LocalDate documentExpiresAt;
    private int sortOrder;
    private String inviteToken;
    private String inviteUrl;
    private String inviteSentAt;
    private String submittedAt;
    private String formPayload;
    private PassengerDocReviewStatus docReviewStatus;
    private UUID latestDocumentId;
    private String latestDocumentKind;
    private List<String> pendingItems;
    private List<PassengerCorrectionResponse> openCorrections;
    private boolean hasTravelerProfileConsent;
    private String createdAt;
    private String updatedAt;
}
