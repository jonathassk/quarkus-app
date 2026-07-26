package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripCommentRead;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripCommentReadRepository
        implements PanacheRepositoryBase<TripCommentRead, TripCommentRead.TripCommentReadId> {

    public Optional<TripCommentRead> findByTripAndUser(UUID tripId, UUID userId) {
        return find("tripId = ?1 and userId = ?2", tripId, userId).firstResultOptional();
    }
}
