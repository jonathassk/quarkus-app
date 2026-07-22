package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_email_preferences")
public class UserEmailPreferences extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    /** Novidades e atualizações de produto (toggle emailUpdates no front). */
    @Column(name = "email_updates", nullable = false)
    @Builder.Default
    private boolean emailUpdates = true;

    /** Lembretes próximos da data de partida (toggle tripReminders no front). */
    @Column(name = "trip_reminders", nullable = false)
    @Builder.Default
    private boolean tripReminders = true;

    /** Alertas de expiração de documentos (toggle documentExpiryAlerts no front). */
    @Column(name = "document_expiry_alerts", nullable = false)
    @Builder.Default
    private boolean documentExpiryAlerts = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
