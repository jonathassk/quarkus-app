package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ProposalAddOn;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProposalAddOnRepository implements PanacheRepositoryBase<ProposalAddOn, UUID> {

    public List<ProposalAddOn> findByVersionId(UUID versionId) {
        return list("version.id = ?1 ORDER BY sortOrder ASC, createdAt ASC", versionId);
    }
}
