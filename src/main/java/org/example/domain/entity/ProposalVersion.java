package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.CommercialProposalStatus;
import org.example.domain.enums.PricingEditMode;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "proposal_versions")
public class ProposalVersion extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private CommercialProposal proposal;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private CommercialProposalStatus status = CommercialProposalStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_edit_mode", nullable = false, length = 16)
    @Builder.Default
    private PricingEditMode pricingEditMode = PricingEditMode.QUICK;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "client_email", length = 255)
    private String clientEmail;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "allow_negotiation", nullable = false)
    @Builder.Default
    private boolean allowNegotiation = false;

    @Column(name = "recommendation_note", columnDefinition = "TEXT")
    private String recommendationNote;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "change_request_types", columnDefinition = "jsonb")
    private List<String> changeRequestTypes;

    @Column(name = "change_request_message", columnDefinition = "TEXT")
    private String changeRequestMessage;

    @Column(name = "change_requested_at")
    private Instant changeRequestedAt;

    @Column(name = "change_requested_by_name", length = 255)
    private String changeRequestedByName;

    @Column(name = "change_requested_by_email", length = 255)
    private String changeRequestedByEmail;

    @Column(name = "below_minimum_justification", columnDefinition = "TEXT")
    private String belowMinimumJustification;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProposalOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProposalItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProposalAddOn> addOns = new ArrayList<>();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProposalAdjustment> adjustments = new ArrayList<>();

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

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
