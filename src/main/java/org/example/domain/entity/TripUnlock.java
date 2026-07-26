package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.TripUnlockKind;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Benefício desbloqueado em uma viagem por pagamento avulso.
 *
 * <p>Cada compra UNITARIO gera uma linha por {@link TripUnlockKind}, todas com o
 * mesmo {@code stripeSessionId}. O par ({@code trip}, {@code kind}) é único, então
 * pagar novamente a mesma viagem não duplica o desbloqueio.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "trip_unlocks",
    uniqueConstraints = @UniqueConstraint(name = "uk_trip_unlocks_trip_kind", columnNames = {"trip_id", "kind"}),
    indexes = {
        @Index(name = "idx_trip_unlocks_session", columnList = "stripe_session_id"),
        @Index(name = "idx_trip_unlocks_user", columnList = "user_id")
    }
)
public class TripUnlock extends PanacheEntityBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** Quem pagou. Nulo se o usuário foi removido depois da compra. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private TripUnlockKind kind;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "stripe_session_id", length = 255)
    private String stripeSessionId;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @PrePersist
    void onCreate() {
        if (paidAt == null) {
            paidAt = Instant.now();
        }
    }
}
