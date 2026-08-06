package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.OpportunityStage;
import org.example.domain.enums.QualificationStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agency_opportunities")
public class AgencyOpportunity extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private AgencyClient client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id")
    private CommercialProposal proposal;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OpportunityStage stage = OpportunityStage.NEW;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_consultant_id")
    private User assignedConsultant;

    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

    @Column(name = "lead_source", nullable = false, length = 64)
    @Builder.Default
    private String leadSource = "OTHER";

    @Column(name = "lead_source_detail", columnDefinition = "TEXT")
    private String leadSourceDetail;

    @Column(name = "preferred_channel", length = 32)
    private String preferredChannel;

    @Column(name = "best_contact_time", length = 128)
    private String bestContactTime;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String country;

    @Column(name = "is_passenger")
    private Boolean passenger;

    @Column(name = "decision_makers", columnDefinition = "TEXT")
    private String decisionMakers;

    @Column(name = "origin_city", length = 128)
    private String originCity;

    @Column(columnDefinition = "TEXT")
    private String destinations;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "dates_flexible", nullable = false)
    @Builder.Default
    private boolean datesFlexible = false;

    @Column(name = "alternate_airports", columnDefinition = "TEXT")
    private String alternateAirports;

    @Column(name = "trip_type", length = 64)
    private String tripType;

    private Integer adults;

    @Column(name = "children_count")
    private Integer childrenCount;

    @Column(name = "children_ages", columnDefinition = "TEXT")
    private String childrenAges;

    private Integer infants;

    private Integer rooms;

    @Column(name = "occupancy_preference", length = 64)
    private String occupancyPreference;

    @Column(name = "passengers_estimated", nullable = false)
    @Builder.Default
    private boolean passengersEstimated = true;

    @Column(name = "desired_services", columnDefinition = "TEXT")
    private String desiredServices;

    @Column(name = "budget_min", precision = 14, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 14, scale = 2)
    private BigDecimal budgetMax;

    @Column(name = "budget_currency", length = 8)
    @Builder.Default
    private String budgetCurrency = "BRL";

    @Column(name = "budget_per_person")
    private Boolean budgetPerPerson;

    @Column(name = "budget_includes_flights")
    private Boolean budgetIncludesFlights;

    @Column(name = "payment_preference", columnDefinition = "TEXT")
    private String paymentPreference;

    @Column(name = "accepts_installments")
    private Boolean acceptsInstallments;

    @Column(name = "budget_estimated_by_agent")
    private Boolean budgetEstimatedByAgent;

    @Column(columnDefinition = "TEXT")
    private String preferences;

    @Column(columnDefinition = "TEXT")
    private String restrictions;

    @Column(name = "decision_deadline")
    private LocalDate decisionDeadline;

    @Column(length = 32)
    private String urgency;

    @Column(name = "has_other_proposals")
    private Boolean hasOtherProposals;

    @Column(name = "has_existing_reservation")
    private Boolean hasExistingReservation;

    @Column(name = "decision_maker", columnDefinition = "TEXT")
    private String decisionMaker;

    @Column(name = "main_criterion", length = 64)
    private String mainCriterion;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_status", nullable = false, length = 32)
    @Builder.Default
    private QualificationStatus qualificationStatus = QualificationStatus.INSUFFICIENT;

    @Column(name = "ready_to_quote_override", nullable = false)
    @Builder.Default
    private boolean readyToQuoteOverride = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private org.example.domain.enums.OpportunityPriority priority =
            org.example.domain.enums.OpportunityPriority.MEDIUM;

    @Column(name = "estimated_value", precision = 14, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_action_type", length = 64)
    private org.example.domain.enums.OpportunityNextActionType nextActionType;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(name = "next_action_note", columnDefinition = "TEXT")
    private String nextActionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_action_assignee_id")
    private User nextActionAssignee;

    @Column(name = "lost_reason", columnDefinition = "TEXT")
    private String lostReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "lost_reason_code", length = 64)
    private org.example.domain.enums.OpportunityLostReasonCode lostReasonCode;

    @Column(name = "lost_competitor", columnDefinition = "TEXT")
    private String lostCompetitor;

    @Column(name = "lost_note", columnDefinition = "TEXT")
    private String lostNote;

    @Column(name = "lost_may_reactivate", nullable = false)
    @Builder.Default
    private boolean lostMayReactivate = false;

    @Column(name = "lost_reactivate_at")
    private LocalDate lostReactivateAt;

    @Column(name = "lost_at")
    private Instant lostAt;

    @Column(name = "won_at")
    private Instant wonAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (lastActivityAt == null) {
            lastActivityAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
