package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.DocumentExpiry;
import org.example.domain.enums.DocumentExpiryKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DocumentExpiryRepository implements PanacheRepositoryBase<DocumentExpiry, UUID> {

    public List<DocumentExpiry> findByUserId(UUID userId) {
        return find("user.id = ?1", Sort.by("kind").and("createdAt"), userId).list();
    }

    public Optional<DocumentExpiry> findByIdAndUserId(UUID id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<DocumentExpiry> findByUserIdAndKind(UUID userId, DocumentExpiryKind kind) {
        return find("user.id = ?1 and kind = ?2", userId, kind).firstResultOptional();
    }
}
