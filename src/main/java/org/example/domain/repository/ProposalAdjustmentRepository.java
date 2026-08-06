package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ProposalAdjustment;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProposalAdjustmentRepository implements PanacheRepositoryBase<ProposalAdjustment, UUID> {

    public List<ProposalAdjustment> findByVersionId(UUID versionId) {
        return list("version.id = ?1 ORDER BY createdAt ASC", versionId);
    }

    public List<ProposalAdjustment> findForOption(UUID versionId, UUID optionId) {
        return list(
                "version.id = ?1 AND (option IS NULL OR option.id = ?2) ORDER BY createdAt ASC",
                versionId, optionId);
    }
}
