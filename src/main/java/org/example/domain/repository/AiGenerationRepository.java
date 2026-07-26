package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.AiGeneration;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AiGenerationRepository implements PanacheRepositoryBase<AiGeneration, UUID> {

    public long countByUserSince(UUID userId, Instant since) {
        return count("user.id = ?1 AND createdAt >= ?2", userId, since);
    }
}
