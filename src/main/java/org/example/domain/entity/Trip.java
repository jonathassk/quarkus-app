package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.TripStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trips")
public class Trip extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "budget_total", precision = 10, scale = 2)
    private BigDecimal budgetTotal;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "duration_days")
    @Builder.Default
    private int durationDays = 1;

    @Column(name = "target_month")
    private Integer targetMonth;

    @Column(name = "cover_image_url", length = 512)
    private String coverImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(length = 20)
    private String visibility; // Pode ser transformado em enum depois

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_status", length = 20)
    private TripStatus status;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripSegment> segments;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripUser> users;

    /**
     * Agência que criou esta viagem.
     * <ul>
     *   <li>{@code null} – viagem pessoal B2C (FREE ou PREMIUM).</li>
     *   <li>non-null    – viagem B2B criada por uma agência; o isolamento multitenant
     *                    é garantido filtrando por este campo em todas as queries da agência.</li>
     * </ul>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_status", length = 32, nullable = false)
    @Builder.Default
    private ProposalStatus proposalStatus = ProposalStatus.DRAFT;

    @Column(name = "base_cost", precision = 12, scale = 2)
    private BigDecimal baseCost;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    /** Código opaco da proposta pública (/p/{shareCode}). */
    @Column(name = "share_code", length = 64)
    private String shareCode;

    @Column(name = "last_contact_at")
    private Instant lastContactAt;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TripProposalTier> proposalTiers = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        syncStatusFromDates();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        syncStatusFromDates();
    }

    private void syncStatusFromDates() {
        this.status = TripStatus.fromDates(startDate, endDate, LocalDate.now());
    }
} 