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

    public boolean existsByVersionAndTrip(UUID versionId, UUID tripId) {
        return count("version.id = ?1 AND trip.id = ?2", versionId, tripId) > 0;
    }

    /**
     * Opção em versão aberta (DRAFT/SENT/…) de outra proposta que já usa este trip.
     */
    public Optional<ProposalOption> findOpenOptionByTripExcludingProposal(UUID tripId, UUID excludeProposalId) {
        return getEntityManager()
                .createQuery(
                        "SELECT o FROM ProposalOption o "
                                + "JOIN o.version v JOIN v.proposal p "
                                + "WHERE o.trip.id = :tripId AND p.id <> :excludeProposalId "
                                + "AND v.status IN :openStatuses "
                                + "AND (p.currentVersion IS NULL OR p.currentVersion.id = v.id)",
                        ProposalOption.class)
                .setParameter("tripId", tripId)
                .setParameter("excludeProposalId", excludeProposalId)
                .setParameter(
                        "openStatuses",
                        java.util.List.of(
                                org.example.domain.enums.CommercialProposalStatus.DRAFT,
                                org.example.domain.enums.CommercialProposalStatus.SENT,
                                org.example.domain.enums.CommercialProposalStatus.VIEWED,
                                org.example.domain.enums.CommercialProposalStatus.CHANGE_REQUESTED))
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public long countVisibleByVersion(UUID versionId) {
        return count("version.id = ?1 AND hidden = false", versionId);
    }
}
