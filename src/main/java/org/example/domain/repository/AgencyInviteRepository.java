package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyInvite;
import org.example.domain.enums.AgencyInviteStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AgencyInviteRepository implements PanacheRepositoryBase<AgencyInvite, UUID> {

    public Optional<AgencyInvite> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return find("token", token.trim()).firstResultOptional();
    }

    public Optional<AgencyInvite> findPendingByAgencyAndEmail(UUID agencyId, String email) {
        return find(
                        "agency.id = ?1 AND lower(email) = ?2 AND status = ?3",
                        agencyId,
                        email.toLowerCase(),
                        AgencyInviteStatus.PENDING)
                .firstResultOptional();
    }

    public List<AgencyInvite> findPendingByAgency(UUID agencyId) {
        return list(
                "agency.id = ?1 AND status = ?2 ORDER BY createdAt DESC",
                agencyId,
                AgencyInviteStatus.PENDING);
    }

    public long countPendingByAgency(UUID agencyId) {
        return count("agency.id = ?1 AND status = ?2", agencyId, AgencyInviteStatus.PENDING);
    }
}
