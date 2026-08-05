package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyOpportunityActivity;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencyOpportunityActivityRepository
        implements PanacheRepositoryBase<AgencyOpportunityActivity, UUID> {

    public List<AgencyOpportunityActivity> listByOpportunity(UUID opportunityId, int limit) {
        int safe = Math.min(Math.max(limit, 1), 200);
        return find(
                        "opportunity.id = ?1 ORDER BY createdAt DESC",
                        opportunityId)
                .page(0, safe)
                .list();
    }
}
