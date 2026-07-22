package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import org.example.domain.entity.TripDocument;

import java.util.List;
import java.util.Optional;

public class TripDocumentRepository implements PanacheRepositoryBase<TripDocument, UUID> {

    public List<TripDocument> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY createdAt DESC", tripId);
    }

    public Optional<TripDocument> findByIdAndTripId(UUID documentId, UUID tripId) {
        return find("id = ?1 AND trip.id = ?2", documentId, tripId).firstResultOptional();
    }
}
