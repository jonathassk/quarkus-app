package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.DocumentExpiryKind;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Documento com data de validade monitorada nas configurações do usuário
 * (passaporte, visto, CNH internacional ou documentos extras cadastrados
 * livremente) usado para disparar alertas de expiração.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_document_expiry")
public class DocumentExpiry extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private DocumentExpiryKind kind = DocumentExpiryKind.CUSTOM;

    /** Nome de exibição — obrigatório para documentos CUSTOM, nulo nos tipos fixos (o front traduz pelo kind). */
    @Column(length = 255)
    private String name;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "alert_enabled", nullable = false)
    @Builder.Default
    private boolean alertEnabled = true;

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
