package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripComment;
import org.example.domain.enums.TripCommentTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripCommentRepository implements PanacheRepositoryBase<TripComment, UUID> {

    public List<TripComment> findActiveByTripId(UUID tripId) {
        return list("trip.id = ?1 and deletedAt is null order by createdAt asc", tripId);
    }

    public List<TripComment> findActiveByTripAndTarget(
            UUID tripId, TripCommentTargetType targetType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return list(
                    "trip.id = ?1 and targetType = ?2 and targetId is null and deletedAt is null order by createdAt asc",
                    tripId,
                    targetType);
        }
        return list(
                "trip.id = ?1 and targetType = ?2 and targetId = ?3 and deletedAt is null order by createdAt asc",
                tripId,
                targetType,
                targetId);
    }

    public Optional<TripComment> findActiveById(UUID commentId) {
        return find("id = ?1 and deletedAt is null", commentId).firstResultOptional();
    }

    public long countUnread(UUID tripId, UUID userId, Instant since) {
        if (since == null) {
            return count("trip.id = ?1 and deletedAt is null and author.id <> ?2", tripId, userId);
        }
        return count(
                "trip.id = ?1 and deletedAt is null and author.id <> ?2 and createdAt > ?3",
                tripId,
                userId,
                since);
    }
}
