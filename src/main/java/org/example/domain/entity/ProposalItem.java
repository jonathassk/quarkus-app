package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.ItemPricingMode;
import org.example.domain.enums.MarkupKind;
import org.example.domain.enums.ProposalItemScope;
import org.example.domain.enums.ProposalItemType;
import org.example.domain.enums.SupplierVisibility;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "proposal_items")
public class ProposalItem extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ProposalVersion version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private ProposalOption option;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ProposalItemScope scope = ProposalItemScope.OPTION;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    @Builder.Default
    private ProposalItemType itemType = ProposalItemType.OTHER;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 512)
    private String subtitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 16)
    @Builder.Default
    private ItemPricingMode pricingMode = ItemPricingMode.COST_PLUS;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency;

    @Column(name = "cost_amount_minor")
    private Long costAmountMinor;

    @Column(name = "fx_rate_micros")
    private Long fxRateMicros;

    @Column(name = "fx_date")
    private LocalDate fxDate;

    @Column(name = "fx_source", length = 64)
    private String fxSource;

    @Column(name = "fx_protection_bps")
    private Integer fxProtectionBps;

    @Column(name = "cost_minor")
    private Long costMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "markup_kind", length = 16)
    private MarkupKind markupKind;

    @Column(name = "markup_value_minor")
    private Long markupValueMinor;

    @Column(name = "markup_percent_bps")
    private Integer markupPercentBps;

    @Column(name = "supplier_public_price_minor")
    private Long supplierPublicPriceMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_kind", length = 16)
    private MarkupKind commissionKind;

    @Column(name = "commission_value_minor")
    private Long commissionValueMinor;

    @Column(name = "commission_percent_bps")
    private Integer commissionPercentBps;

    @Column(name = "service_fee_minor", nullable = false)
    @Builder.Default
    private long serviceFeeMinor = 0;

    @Column(name = "client_price_minor")
    private Long clientPriceMinor;

    @Column(name = "expected_commission_minor")
    private Long expectedCommissionMinor;

    @Column(name = "expected_revenue_minor")
    private Long expectedRevenueMinor;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_visibility", nullable = false, length = 32)
    @Builder.Default
    private SupplierVisibility supplierVisibility = SupplierVisibility.SHOW_NAME;

    @Column(name = "optional_flag", nullable = false)
    @Builder.Default
    private boolean optional = false;

    @Column(name = "hide_price", nullable = false)
    @Builder.Default
    private boolean hidePrice = false;

    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;

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
