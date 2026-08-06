package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.application.dto.proposal.BaseCostItemDTO;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.TripStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

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

    /**
     * Detalhes de voos/passagens já comprados pelo usuário (localizadores, companhia, horários).
     * A aplicação não vende passagens — apenas registra o que o viajante já adquiriu.
     */
    @Column(name = "flight_details", columnDefinition = "TEXT")
    private String flightDetails;

    /**
     * Detalhes de hospedagem já reservada (hotel, endereço, check-in/out, confirmação).
     * A aplicação não vende hospedagem — apenas registra o que o viajante já adquiriu.
     */
    @Column(name = "hotel_details", columnDefinition = "TEXT")
    private String hotelDetails;

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

    /**
     * Quando true, o agente pode mover a proposta para {@link ProposalStatus#NEGOTIATING}
     * após o envio. Definido no momento do envio.
     */
    @Column(name = "allow_negotiation", nullable = false)
    @Builder.Default
    private boolean allowNegotiation = false;

    /** Subestado operacional pós-venda (badge; não é coluna do kanban). */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", length = 32)
    private OperationStatus operationStatus;

    @Column(name = "base_cost", precision = 12, scale = 2)
    private BigDecimal baseCost;

    /**
     * Breakdown do custo base (voo, hospedagem, seguro, passeios, extras).
     * A soma deve coincidir com {@link #baseCost}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_cost_items", columnDefinition = "jsonb")
    private List<BaseCostItemDTO> baseCostItems;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    /** Código opaco da proposta pública (/p/{shareCode}). */
    @Column(name = "share_code", length = 64)
    private String shareCode;

    @Column(name = "last_contact_at")
    private Instant lastContactAt;

    /** Destinatário da proposta pública — permite reenvio sem redigitar o contato. */
    @Column(name = "proposal_client_email", length = 255)
    private String proposalClientEmail;

    @Column(name = "proposal_client_name", length = 255)
    private String proposalClientName;

    @Column(name = "proposal_sent_at")
    private Instant proposalSentAt;

    /** Após esta data a proposta pública não aceita aprovação nem pagamento. */
    @Column(name = "proposal_expires_at")
    private Instant proposalExpiresAt;

    @Column(name = "proposal_reject_reason", columnDefinition = "TEXT")
    private String proposalRejectReason;

    /** Última abertura da proposta pública pelo cliente. */
    @Column(name = "proposal_last_viewed_at")
    private Instant proposalLastViewedAt;

    @Column(name = "proposal_view_count")
    @Builder.Default
    private Integer proposalViewCount = 0;

    @Column(name = "proposal_views_today")
    @Builder.Default
    private Integer proposalViewsToday = 0;

    /** Dia civil ao qual {@link #proposalViewsToday} se refere. */
    @Column(name = "proposal_views_day")
    private LocalDate proposalViewsDay;

    /** Cliente CRM da agência (épico 5). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private AgencyClient client;

    /** Consultor responsável — owner pode reatribuir. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_consultant_id")
    private User assignedConsultant;

    /** Viagem gerada pelo seed de demonstração do onboarding. */
    @Column(name = "is_demo", nullable = false)
    @Builder.Default
    private boolean demo = false;

    /** Próximo follow-up agendado após envio da proposta. */
    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

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
        // Com datas fixas, o calendário manda. Sem datas (viagem relativa),
        // preserva status manual (ex.: PLANNING → ONGOING via PATCH).
        if (startDate != null && endDate != null) {
            this.status = TripStatus.fromDates(startDate, endDate, LocalDate.now());
        } else if (this.status == null) {
            this.status = TripStatus.PLANNING;
        }
    }
} 