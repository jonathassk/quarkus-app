package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencyClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AgencyClientRepository implements PanacheRepositoryBase<AgencyClient, UUID> {

    public List<AgencyClient> findByAgencyId(UUID agencyId) {
        return list("agency.id = ?1 ORDER BY name ASC", agencyId);
    }

    public Optional<AgencyClient> findByAgencyAndEmail(UUID agencyId, String email) {
        return find("agency.id = ?1 AND lower(email) = ?2", agencyId, email.toLowerCase())
                .firstResultOptional();
    }

    public List<AgencyClient> search(UUID agencyId, String q, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        if (q == null || q.isBlank()) {
            return find("agency.id = ?1 ORDER BY name ASC", agencyId)
                    .page(safePage, safeSize)
                    .list();
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        return find(
                        "agency.id = ?1 AND (lower(name) LIKE ?2 OR lower(coalesce(email,'')) LIKE ?2 OR lower(coalesce(phone,'')) LIKE ?2) ORDER BY name ASC",
                        agencyId,
                        like)
                .page(safePage, safeSize)
                .list();
    }

    public long countSearch(UUID agencyId, String q) {
        if (q == null || q.isBlank()) {
            return count("agency.id = ?1", agencyId);
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        return count(
                "agency.id = ?1 AND (lower(name) LIKE ?2 OR lower(coalesce(email,'')) LIKE ?2 OR lower(coalesce(phone,'')) LIKE ?2)",
                agencyId,
                like);
    }
}
