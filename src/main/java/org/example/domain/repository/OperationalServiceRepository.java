package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.OperationalService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OperationalServiceRepository implements PanacheRepositoryBase<OperationalService, UUID> {

    public List<OperationalService> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY sortOrder ASC, createdAt ASC", tripId);
    }

    public Optional<OperationalService> findByProposalItemId(UUID proposalItemId) {
        return find("proposalItem.id", proposalItemId).firstResultOptional();
    }

    public long countByTripId(UUID tripId) {
        return count("trip.id", tripId);
    }
}
