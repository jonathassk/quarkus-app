package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripShareLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripShareLinkRepository implements PanacheRepositoryBase<TripShareLink, UUID> {

    public Optional<TripShareLink> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return find("code = ?1", code.trim()).firstResultOptional();
    }

    public List<TripShareLink> findByTripId(UUID tripId) {
        return list("trip.id = ?1 order by createdAt desc", tripId);
    }

    public List<TripShareLink> findActiveByTripId(UUID tripId) {
        return list(
                "trip.id = ?1 and revokedAt is null and (expiresAt is null or expiresAt > ?2) order by createdAt desc",
                tripId,
                java.time.Instant.now());
    }
}
