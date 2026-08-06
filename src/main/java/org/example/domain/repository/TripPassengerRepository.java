package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripPassenger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripPassengerRepository implements PanacheRepositoryBase<TripPassenger, UUID> {

    public List<TripPassenger> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY sortOrder ASC, createdAt ASC, id ASC", tripId);
    }

    public Optional<TripPassenger> findByIdAndTripId(UUID passengerId, UUID tripId) {
        return find("id = ?1 AND trip.id = ?2", passengerId, tripId).firstResultOptional();
    }

    public Optional<TripPassenger> findByInviteToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return find("inviteToken", token.trim()).firstResultOptional();
    }

    public long countByTripId(UUID tripId) {
        return count("trip.id", tripId);
    }

    public int nextSortOrder(UUID tripId) {
        Integer max = getEntityManager()
                .createQuery(
                        "SELECT MAX(p.sortOrder) FROM TripPassenger p WHERE p.trip.id = :tid",
                        Integer.class)
                .setParameter("tid", tripId)
                .getSingleResult();
        return max == null ? 0 : max + 1;
    }
}
