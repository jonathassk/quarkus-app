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
     * Plano da agência. Não há tier gratuito: só planos pagos ({@code B2B_PRO} hoje;
     * futuros {@code B2B_*}). {@code B2B_INACTIVE} = assinatura cancelada / sem acesso ao portal.
     */
    @Column(name = "plan_type", length = 50)
    @Builder.Default
    private String planType = "B2B_INACTIVE";

    /** WhatsApp E.164 ou dígitos (ex.: 5511999999999) para CTA da proposta. */
    @Column(name = "whatsapp_number", length = 32)
    private String whatsappNumber;

    /** Markup padrão (%) aplicado sobre o custo base das propostas. */
    @Column(name = "markup_percentage", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private java.math.BigDecimal markupPercentage = java.math.BigDecimal.ZERO;

    /** ID da assinatura Stripe vinculada a esta agência. */
    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    /** Passo atual do wizard de onboarding B2B. */
    @Column(name = "onboarding_step", length = 32, nullable = false)
    @Builder.Default
    private String onboardingStep = "WELCOME";

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "onboarding_skipped_at")
    private Instant onboardingSkippedAt;

    @Column(name = "onboarding_trip_id", columnDefinition = "uuid")
    private UUID onboardingTripId;

    @Column(name = "onboarding_client_id", columnDefinition = "uuid")
    private UUID onboardingClientId;

    @Column(name = "demo_data_active", nullable = false)
    @Builder.Default
    private boolean demoDataActive = false;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "agent_title", length = 128)
    private String agentTitle;

    @Column(name = "agent_photo_url", length = 512)
    private String agentPhotoUrl;

    @Column(name = "website_or_instagram", length = 255)
    private String websiteOrInstagram;

    /** PERCENTAGE | FEE | COMMISSION | MIXED | NONE */
    @Column(name = "pricing_model", length = 32)
    private String pricingModel;

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
