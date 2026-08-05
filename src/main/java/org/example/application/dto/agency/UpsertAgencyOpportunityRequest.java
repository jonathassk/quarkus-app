package org.example.application.dto.agency;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertAgencyOpportunityRequest {
    /** Cliente existente; se ausente, cria contato com clientName + phone/email. */
    private UUID clientId;
    private String clientName;
    private String clientEmail;
    private String clientPhone;

    private String title;
    private String stage;
    private String requestSummary;
    private UUID assignedConsultantId;
    private Instant nextFollowUpAt;
    private String leadSource;
    private String leadSourceDetail;

    private String preferredChannel;
    private String bestContactTime;
    private String city;
    private String country;
    private Boolean passenger;
    private String decisionMakers;

    private String originCity;
    private String destinations;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationDays;
    private Boolean datesFlexible;
    private String alternateAirports;
    private String tripType;

    private Integer adults;
    private Integer childrenCount;
    private String childrenAges;
    private Integer infants;
    private Integer rooms;
    private String occupancyPreference;
    private Boolean passengersEstimated;

    private List<String> desiredServices;

    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private String budgetCurrency;
    private Boolean budgetPerPerson;
    private Boolean budgetIncludesFlights;
    private String paymentPreference;
    private Boolean acceptsInstallments;
    private Boolean budgetEstimatedByAgent;

    private List<String> preferences;
    private String restrictions;

    private LocalDate decisionDeadline;
    private String urgency;
    private Boolean hasOtherProposals;
    private Boolean hasExistingReservation;
    private String decisionMaker;
    private String mainCriterion;

    private String qualificationStatus;
    private Boolean readyToQuoteOverride;

    private String priority;
    private BigDecimal estimatedValue;
    private String nextActionType;
    private Instant nextActionAt;
    private String nextActionNote;
    private UUID nextActionAssigneeId;

    private String lostReason;
    private String lostReasonCode;
    private String lostCompetitor;
    private String lostNote;
    private Boolean lostMayReactivate;
    private LocalDate lostReactivateAt;

    /** Nota livre registrada na timeline ao salvar. */
    private String activityNote;
}
