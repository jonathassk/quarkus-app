package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.CommercialProposal;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CommercialProposalRepository implements PanacheRepositoryBase<CommercialProposal, UUID> {

    public Optional<CommercialProposal> findByShareCode(String shareCode) {
        if (shareCode == null || shareCode.isBlank()) {
            return Optional.empty();
        }
        return find("shareCode", shareCode.trim()).firstResultOptional();
    }

    public Optional<CommercialProposal> findByOpportunityId(UUID opportunityId) {
        return find("opportunity.id", opportunityId).firstResultOptional();
    }

    public Optional<CommercialProposal> findByTripId(UUID tripId) {
        return getEntityManager()
                .createQuery(
                        "SELECT o.version.proposal FROM ProposalOption o WHERE o.trip.id = :tid ORDER BY o.createdAt DESC",
                        CommercialProposal.class)
                .setParameter("tid", tripId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
