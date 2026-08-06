package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.OperationalNextAction;
import org.example.domain.enums.OperationalServiceStatus;
import org.example.domain.enums.OperationalServiceType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "operational_services")
public class OperationalService extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_item_id")
    private ProposalItem proposalItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private AgencySupplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 32)
    @Builder.Default
    private OperationalServiceType serviceType = OperationalServiceType.OTHER;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 512)
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OperationalServiceStatus status = OperationalServiceStatus.TO_RESERVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_action", length = 64)
    private OperationalNextAction nextAction;

    @Column(name = "next_action_label", length = 255)
    private String nextActionLabel;

    @Column(name = "next_action_due_at")
    private Instant nextActionDueAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_info_json", columnDefinition = "jsonb")
    private Map<String, Object> publicInfo;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "cost_estimated_minor")
    private Long costEstimatedMinor;

    @Column(name = "price_approved_minor")
    private Long priceApprovedMinor;

    @Column(length = 3)
    private String currency;

    @Column(name = "confirmed_cost_minor")
    private Long confirmedCostMinor;

    @Column(name = "cost_divergence_minor")
    private Long costDivergenceMinor;

    @Column(length = 128)
    private String locator;

    @Column(name = "ticket_number", length = 128)
    private String ticketNumber;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancellation_policy", columnDefinition = "TEXT")
    private String cancellationPolicy;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    @Column(name = "estimated_penalty_minor")
    private Long estimatedPenaltyMinor;

    @Column(name = "supplier_credit_minor")
    private Long supplierCreditMinor;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "operational_service_passengers",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "passenger_id"))
    @Builder.Default
    private Set<TripPassenger> passengers = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
