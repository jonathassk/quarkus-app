package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripProposalTier;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TripProposalTierRepository implements PanacheRepositoryBase<TripProposalTier, UUID> {

    public List<TripProposalTier> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY sortOrder ASC, code ASC", tripId);
    }

    public void deleteByTripId(UUID tripId) {
        delete("trip.id", tripId);
    }
}
