package org.example.application.dto.agency;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencyClientDTO {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String notes;
    private List<String> tags;
    private UUID userId;
    private Instant createdAt;
    private Instant updatedAt;

    private String birthPlace;
    private String nationality;
    private String documentNumber;
    private String documentType;
    private LocalDate documentIssuedAt;
    private LocalDate documentExpiresAt;
    private LocalDate birthDate;
    private String gender;

    /** Preenchido na ficha 360. */
    private List<ClientTripSummaryDTO> trips;
    private long tripCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientTripSummaryDTO {
        private UUID tripId;
        private String name;
        private String proposalStatus;
        private String shareCode;
        private java.math.BigDecimal finalPrice;
        private Instant updatedAt;
    }
}
