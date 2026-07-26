package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripSegmentRevision;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripSegmentRevisionRepository implements PanacheRepositoryBase<TripSegmentRevision, UUID> {

    public Optional<TripSegmentRevision> findLatestBySegment(UUID segmentId) {
        return find("segmentId = ?1 ORDER BY createdAt DESC", segmentId).firstResultOptional();
    }
}
