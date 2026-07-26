package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ProposalAcceptance;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProposalAcceptanceRepository implements PanacheRepositoryBase<ProposalAcceptance, UUID> {

    public List<ProposalAcceptance> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY acceptedAt DESC", tripId);
    }
}
