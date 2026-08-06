package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
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
@Table(name = "proposal_addons")
public class ProposalAddOn extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ProposalVersion version;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_minor", nullable = false)
    @Builder.Default
    private long priceMinor = 0;

    /** TOTAL | PER_PERSON */
    @Column(name = "pricing_unit", nullable = false, length = 16)
    @Builder.Default
    private String pricingUnit = "TOTAL";

    @Column(name = "quantity_default", nullable = false)
    @Builder.Default
    private int quantityDefault = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligible_option_ids", columnDefinition = "jsonb")
    private List<UUID> eligibleOptionIds;

    @Column(name = "required_flag", nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "optional_flag", nullable = false)
    @Builder.Default
    private boolean optional = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

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
