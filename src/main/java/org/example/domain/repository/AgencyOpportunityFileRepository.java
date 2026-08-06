package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyOpportunityFile;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencyOpportunityFileRepository implements PanacheRepositoryBase<AgencyOpportunityFile, UUID> {

    public List<AgencyOpportunityFile> listByOpportunity(UUID opportunityId) {
        return list("opportunity.id = ?1 ORDER BY createdAt DESC", opportunityId);
    }
}
