package org.example.domain.entity.chat;

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
@Table(name = "user_follows")
public class UserFollow extends PanacheEntityBase {

    @EmbeddedId
    private UserFollowId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserFollowId implements java.io.Serializable {
        @Column(name = "follower_id", columnDefinition = "uuid")
        private UUID followerId;

        @Column(name = "following_id", columnDefinition = "uuid")
        private UUID followingId;
    }
}
