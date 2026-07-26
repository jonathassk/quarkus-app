package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de webhook do Stripe já processado.
 *
 * <p>O Stripe reenvia o mesmo {@code event.id} quando não recebe 2xx dentro da
 * janela de retry. Registrar cada id consumido evita reaplicar upgrades de plano
 * ou desbloqueios de viagem em reprocessamentos.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "stripe_events",
    uniqueConstraints = @UniqueConstraint(name = "uk_stripe_events_event_id", columnNames = "event_id")
)
public class StripeEvent extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    /** Identificador do evento no Stripe (ex.: {@code evt_1PabcDEfgh}). */
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
