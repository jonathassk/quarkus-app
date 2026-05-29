package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.TripDocument;

import java.util.List;
import java.util.Optional;

public class TripDocumentRepository implements PanacheRepository<TripDocument> {

    public List<TripDocument> findByTripId(Long tripId) {
        return list("trip.id = ?1 ORDER BY createdAt DESC", tripId);
    }

    public Optional<TripDocument> findByIdAndTripId(Long documentId, Long tripId) {
        return find("id = ?1 AND trip.id = ?2", documentId, tripId).firstResultOptional();
    }
}
