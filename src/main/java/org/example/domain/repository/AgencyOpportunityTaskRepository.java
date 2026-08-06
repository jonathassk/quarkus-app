package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyOpportunityFile;
import org.example.domain.entity.AgencyOpportunityTask;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencyOpportunityTaskRepository implements PanacheRepositoryBase<AgencyOpportunityTask, UUID> {

    public List<AgencyOpportunityTask> listByOpportunity(UUID opportunityId) {
        return list("opportunity.id = ?1 ORDER BY status ASC, dueAt ASC, createdAt DESC", opportunityId);
    }
}
