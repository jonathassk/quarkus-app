package org.example.domain.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trip_comment_reads")
@IdClass(TripCommentRead.TripCommentReadId.class)
public class TripCommentRead extends PanacheEntityBase {

    @Id
    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    @PrePersist
    void onCreate() {
        if (lastReadAt == null) {
            lastReadAt = Instant.now();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TripCommentReadId implements Serializable {
        private UUID tripId;
        private UUID userId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TripCommentReadId that)) return false;
            return Objects.equals(tripId, that.tripId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tripId, userId);
        }
    }
}
