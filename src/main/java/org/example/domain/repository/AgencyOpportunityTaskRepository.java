package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyOpportunityTask;
import org.example.domain.enums.OpportunityNextActionType;
import org.example.domain.enums.OpportunityTaskOrigin;
import org.example.domain.enums.OpportunityTaskStatus;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencyOpportunityTaskRepository implements PanacheRepositoryBase<AgencyOpportunityTask, UUID> {

    public List<AgencyOpportunityTask> listByOpportunity(UUID opportunityId) {
        return list("opportunity.id = ?1 ORDER BY nextAction DESC, status ASC, dueAt ASC, createdAt DESC",
                opportunityId);
    }

    public List<AgencyOpportunityTask> listOpenByAgency(UUID agencyId) {
        return list(
                "agency.id = ?1 AND status IN (?2, ?3) ORDER BY dueAt ASC NULLS LAST, createdAt DESC",
                agencyId,
                OpportunityTaskStatus.OPEN,
                OpportunityTaskStatus.WAITING);
    }

    public boolean existsOpenAutomation(
            UUID opportunityId, OpportunityNextActionType actionKind) {
        long count = count(
                "opportunity.id = ?1 AND actionKind = ?2 AND origin = ?3 AND status IN (?4, ?5)",
                opportunityId,
                actionKind,
                OpportunityTaskOrigin.AUTOMATION,
                OpportunityTaskStatus.OPEN,
                OpportunityTaskStatus.WAITING);
        return count > 0;
    }

    /** Idempotência por passageiro: note contém o UUID do passageiro. */
    public boolean existsOpenAutomationForPassengerNote(
            UUID opportunityId, OpportunityNextActionType actionKind, UUID passengerId) {
        if (passengerId == null) {
            return existsOpenAutomation(opportunityId, actionKind);
        }
        String marker = "passengerId=" + passengerId;
        long count = count(
                "opportunity.id = ?1 AND actionKind = ?2 AND origin = ?3 AND status IN (?4, ?5) AND note LIKE ?6",
                opportunityId,
                actionKind,
                OpportunityTaskOrigin.AUTOMATION,
                OpportunityTaskStatus.OPEN,
                OpportunityTaskStatus.WAITING,
                "%" + marker + "%");
        return count > 0;
    }

    public boolean existsOpenActionKind(UUID opportunityId, OpportunityNextActionType actionKind) {
        long count = count(
                "opportunity.id = ?1 AND actionKind = ?2 AND status IN (?3, ?4)",
                opportunityId,
                actionKind,
                OpportunityTaskStatus.OPEN,
                OpportunityTaskStatus.WAITING);
        return count > 0;
    }

    public AgencyOpportunityTask findOpenFollowUp(UUID opportunityId) {
        return find(
                "opportunity.id = ?1 AND actionKind = ?2 AND status IN (?3, ?4) ORDER BY dueAt ASC",
                opportunityId,
                OpportunityNextActionType.FOLLOW_UP,
                OpportunityTaskStatus.OPEN,
                OpportunityTaskStatus.WAITING)
                .firstResult();
    }
}
