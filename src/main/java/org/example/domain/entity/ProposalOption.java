package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.ProposalOptionPosition;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "proposal_options")
public class ProposalOption extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ProposalVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_code", nullable = false, length = 32)
    @Builder.Default
    private ProposalOptionPosition position = ProposalOptionPosition.RECOMMENDED;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean recommended = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 512)
    private String subtitle;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "cover_image_url", length = 512)
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "includes_json", columnDefinition = "jsonb")
    private List<String> includes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "excludes_json", columnDefinition = "jsonb")
    private List<String> excludes;

    @Column(name = "payment_conditions", columnDefinition = "TEXT")
    private String paymentConditions;

    @Column(name = "supplier_cost_minor", nullable = false)
    @Builder.Default
    private long supplierCostMinor = 0;

    @Column(name = "markup_amount_minor", nullable = false)
    @Builder.Default
    private long markupAmountMinor = 0;

    @Column(name = "service_fee_minor", nullable = false)
    @Builder.Default
    private long serviceFeeMinor = 0;

    @Column(name = "commission_minor", nullable = false)
    @Builder.Default
    private long commissionMinor = 0;

    @Column(name = "client_price_minor", nullable = false)
    @Builder.Default
    private long clientPriceMinor = 0;

    @Column(name = "expected_revenue_minor", nullable = false)
    @Builder.Default
    private long expectedRevenueMinor = 0;

    /** Margem em basis points (13,1% = 1310). Null se custo indisponível. */
    @Column(name = "margin_bps")
    private Integer marginBps;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
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
