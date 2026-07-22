package org.example.domain.entity.chat;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.entity.User;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_privacy_settings")
public class UserPrivacySettings extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "public_profile", nullable = false)
    @Builder.Default
    private boolean publicProfile = false;

    @Column(name = "allow_dm_public", nullable = false)
    @Builder.Default
    private boolean allowDmPublic = true;

    @Column(name = "allow_dm_followers_only", nullable = false)
    @Builder.Default
    private boolean allowDmFollowersOnly = true;

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
