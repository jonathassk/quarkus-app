package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Agência de viagens — tenant B2B no isolamento multitenant lógico.
 *
 * <p>Cada agência possui seu próprio conjunto de viagens (Trip.agencyId = this.id).
 * Membros da agência (AgencyMember) com papel AGENCY_OWNER têm visibilidade total
 * das viagens da agência; AGENCY_CONSULTANT vê apenas as suas próprias ou onde foi adicionado.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "agencies",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_agency_slug", columnNames = {"slug"})
    }
)
public class Agency extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Slug único para identificação da agência (ex.: "viagens-brasil").
     * Usado como chave de isolamento em queries multitenant.
     */
    @Column(nullable = false, length = 100, unique = true)
    private String slug;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    @Builder.Default
    private String primaryColor = "#000000";

    /**
     * Plano da agência: B2B_FREE, B2B_PRO, etc.
     */
    @Column(name = "plan_type", length = 50)
    @Builder.Default
    private String planType = "B2B_FREE";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
