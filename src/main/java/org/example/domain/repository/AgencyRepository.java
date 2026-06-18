package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.Agency;

import java.util.Optional;

@ApplicationScoped
public class AgencyRepository implements PanacheRepository<Agency> {

    public Optional<Agency> findBySlug(String slug) {
        return find("slug", slug.trim().toLowerCase()).firstResultOptional();
    }
}
