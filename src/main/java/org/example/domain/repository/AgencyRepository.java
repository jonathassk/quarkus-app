package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.Agency;

import java.util.Optional;

@ApplicationScoped
public class AgencyRepository implements PanacheRepositoryBase<Agency, UUID> {

    public Optional<Agency> findBySlug(String slug) {
        return find("slug", slug.trim().toLowerCase()).firstResultOptional();
    }
}
