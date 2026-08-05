package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyOpportunity;
import org.example.domain.enums.OpportunityStage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AgencyOpportunityRepository implements PanacheRepositoryBase<AgencyOpportunity, UUID> {

    public List<AgencyOpportunity> search(
            UUID agencyId,
            OpportunityStage stage,
            UUID consultantId,
            UUID clientId,
            String q,
            int page,
            int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        StringBuilder jpql = new StringBuilder("agency.id = ?1");
        List<Object> params = new ArrayList<>();
        params.add(agencyId);
        int idx = 2;

        if (stage != null) {
            jpql.append(" AND stage = ?").append(idx++);
            params.add(stage);
        }
        if (consultantId != null) {
            jpql.append(" AND assignedConsultant.id = ?").append(idx++);
            params.add(consultantId);
        }
        if (clientId != null) {
            jpql.append(" AND client.id = ?").append(idx++);
            params.add(clientId);
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            jpql.append(" AND (")
                    .append("lower(title) LIKE ?").append(idx)
                    .append(" OR lower(coalesce(requestSummary, '')) LIKE ?").append(idx)
                    .append(" OR lower(coalesce(destinations, '')) LIKE ?").append(idx)
                    .append(" OR lower(client.name) LIKE ?").append(idx)
                    .append(" OR lower(coalesce(client.email, '')) LIKE ?").append(idx)
                    .append(" OR lower(coalesce(client.phone, '')) LIKE ?").append(idx)
                    .append(")");
            params.add(like);
            idx++;
        }
        jpql.append(" ORDER BY updatedAt DESC");

        return find(jpql.toString(), params.toArray())
                .page(safePage, safeSize)
                .list();
    }

    public List<AgencyOpportunity> listByClient(UUID agencyId, UUID clientId) {
        return list(
                "agency.id = ?1 AND client.id = ?2 ORDER BY updatedAt DESC",
                agencyId,
                clientId);
    }
}
