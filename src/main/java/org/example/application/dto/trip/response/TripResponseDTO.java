package org.example.application.dto.trip.response;

import java.util.UUID;

import lombok.*;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.TripUserDTO;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TripResponseDTO {
    private UUID id;
    /** Derived from {@code startDate}/{@code endDate} and today (PLANNING / ONGOING / COMPLETED). */
    private TripStatus status;
    private String name;
    private String description;
    /** Passagens já compradas pelo usuário (texto livre). */
    private String flightDetails;
    /** Hospedagem já reservada pelo usuário (texto livre). */
    private String hotelDetails;
    private BigDecimal budgetTotal;
    private LocalDate startDate;
    private LocalDate endDate;
    private int durationDays;
    private Integer targetMonth;
    private String coverImageUrl;
    private String visibility;
    private UUID workspaceId;
    private List<TripSegmentDTO> segments;
    private List<TripUserDTO> users;
    private UUID createdBy;
    private UUID agencyId;
    private ProposalStatus proposalStatus;
    private boolean allowNegotiation;
    private OperationStatus operationStatus;
    private BigDecimal baseCost;
    private BigDecimal finalPrice;
    private String shareCode;
    private String currency;
    /** Destinatário do último envio da proposta. */
    private String proposalClientEmail;
    private String proposalClientName;
    private java.time.Instant proposalSentAt;
    /** Follow-up agendado após o envio. */
    private java.time.Instant nextFollowUpAt;
    /** Viagem gerada pelo seed de demonstração do onboarding. */
    private boolean demo;
    /** Export em PDF liberado nesta viagem por pagamento avulso. */
    private boolean unlockedExportPdf;
    /** Gerações de IA liberadas nesta viagem por pagamento avulso. */
    private boolean unlockedAi;
    /** Verdadeiro quando algum benefício avulso foi liberado nesta viagem. */
    private boolean unlocked;
}
