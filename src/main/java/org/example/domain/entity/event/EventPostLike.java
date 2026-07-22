package org.example.domain.entity.event;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.example.domain.entity.User;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "event_post_likes")
@IdClass(EventPostLike.EventPostLikeId.class)
public class EventPostLike extends PanacheEntityBase {

    @Id
    @Column(name = "post_id", columnDefinition = "uuid")
    private UUID postId;

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    private EventPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventPostLikeId implements Serializable {
        private UUID postId;
        private UUID userId;
    }
}
