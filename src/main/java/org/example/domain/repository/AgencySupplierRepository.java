package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AgencySupplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AgencySupplierRepository implements PanacheRepositoryBase<AgencySupplier, UUID> {

    public List<AgencySupplier> findByAgencyId(UUID agencyId) {
        return list("agency.id = ?1 ORDER BY name ASC", agencyId);
    }

    public Optional<AgencySupplier> findByAgencyAndNameIgnoreCase(UUID agencyId, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return find("agency.id = ?1 AND lower(name) = lower(?2)", agencyId, name.trim())
                .firstResultOptional();
    }
}
