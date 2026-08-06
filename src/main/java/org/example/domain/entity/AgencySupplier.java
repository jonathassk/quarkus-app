package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.SupplierCategory;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agency_suppliers")
public class AgencySupplier extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    @Builder.Default
    private SupplierCategory category = SupplierCategory.OTHER;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(length = 255)
    private String email;

    @Column(length = 64)
    private String whatsapp;

    @Column(length = 512)
    private String website;

    @Column(length = 64)
    private String currencies;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "default_policy", columnDefinition = "TEXT")
    private String defaultPolicy;

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
