package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripTemplate;
import org.example.domain.enums.TripTemplateKind;
import org.example.domain.enums.TripTemplateScope;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TripTemplateRepository implements PanacheRepositoryBase<TripTemplate, UUID> {

    public List<TripTemplate> findPersonal(UUID ownerId, TripTemplateKind kind) {
        if (kind == null) {
            return list("scope = ?1 AND ownerId = ?2 ORDER BY updatedAt DESC",
                    TripTemplateScope.PERSONAL, ownerId);
        }
        return list("scope = ?1 AND ownerId = ?2 AND kind = ?3 ORDER BY updatedAt DESC",
                TripTemplateScope.PERSONAL, ownerId, kind);
    }

    public List<TripTemplate> findAgency(UUID agencyId, TripTemplateKind kind) {
        if (kind == null) {
            return list("scope = ?1 AND agencyId = ?2 ORDER BY updatedAt DESC",
                    TripTemplateScope.AGENCY, agencyId);
        }
        return list("scope = ?1 AND agencyId = ?2 AND kind = ?3 ORDER BY updatedAt DESC",
                TripTemplateScope.AGENCY, agencyId, kind);
    }
}
