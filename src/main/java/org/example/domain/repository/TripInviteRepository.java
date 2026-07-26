package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripInvite;
import org.example.domain.enums.TripInviteStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripInviteRepository implements PanacheRepositoryBase<TripInvite, UUID> {

    public Optional<TripInvite> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return find("token = ?1", token.trim()).firstResultOptional();
    }

    public List<TripInvite> findByTripId(UUID tripId) {
        return list("trip.id = ?1 order by createdAt desc", tripId);
    }

    public Optional<TripInvite> findPendingByTripAndEmail(UUID tripId, String email) {
        return find(
                "trip.id = ?1 and lower(email) = ?2 and status = ?3",
                tripId,
                email.toLowerCase(),
                TripInviteStatus.PENDING)
                .firstResultOptional();
    }
}
