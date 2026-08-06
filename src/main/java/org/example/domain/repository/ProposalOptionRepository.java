package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ProposalOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProposalOptionRepository implements PanacheRepositoryBase<ProposalOption, UUID> {

    public List<ProposalOption> findByVersionId(UUID versionId) {
        return list("version.id = ?1 ORDER BY sortOrder ASC, createdAt ASC", versionId);
    }

    public Optional<ProposalOption> findByTripId(UUID tripId) {
        return find("trip.id = ?1 ORDER BY createdAt DESC", tripId).firstResultOptional();
    }

    public long countVisibleByVersion(UUID versionId) {
        return count("version.id = ?1 AND hidden = false", versionId);
    }
}
