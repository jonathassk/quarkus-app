package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.enums.AgencyRole;

import java.time.Instant;

/**
 * Vínculo entre um User e uma Agency, com papel definido por {@link AgencyRole}.
 *
 * <ul>
 *   <li>AGENCY_OWNER      – dono da agência</li>
 *   <li>AGENCY_CONSULTANT – consultor / funcionário</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "agency_members",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_agency_user", columnNames = {"agency_id", "user_id"})
    }
)
public class AgencyMember extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency_role", nullable = false, length = 50)
    private AgencyRole agencyRole;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
