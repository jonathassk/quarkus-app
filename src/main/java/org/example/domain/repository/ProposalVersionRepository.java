package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ProposalVersion;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProposalVersionRepository implements PanacheRepositoryBase<ProposalVersion, UUID> {

    public List<ProposalVersion> findByProposalId(UUID proposalId) {
        return list("proposal.id = ?1 ORDER BY versionNumber DESC", proposalId);
    }

    public int nextVersionNumber(UUID proposalId) {
        Integer max = getEntityManager()
                .createQuery(
                        "SELECT MAX(v.versionNumber) FROM ProposalVersion v WHERE v.proposal.id = :pid",
                        Integer.class)
                .setParameter("pid", proposalId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
