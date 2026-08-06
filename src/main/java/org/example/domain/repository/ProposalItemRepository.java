package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ProposalItem;
import org.example.domain.enums.ProposalItemScope;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProposalItemRepository implements PanacheRepositoryBase<ProposalItem, UUID> {

    public List<ProposalItem> findByVersionId(UUID versionId) {
        return list("version.id = ?1 ORDER BY sortOrder ASC, createdAt ASC", versionId);
    }

    public List<ProposalItem> findCommonByVersion(UUID versionId) {
        return list("version.id = ?1 AND scope = ?2 ORDER BY sortOrder ASC",
                versionId, ProposalItemScope.COMMON);
    }

    public List<ProposalItem> findByOptionId(UUID optionId) {
        return list("option.id = ?1 ORDER BY sortOrder ASC, createdAt ASC", optionId);
    }

    public List<ProposalItem> findForOptionPricing(UUID versionId, UUID optionId) {
        return list(
                "(version.id = ?1 AND scope = ?2) OR (option.id = ?3) ORDER BY sortOrder ASC",
                versionId, ProposalItemScope.COMMON, optionId);
    }
}
