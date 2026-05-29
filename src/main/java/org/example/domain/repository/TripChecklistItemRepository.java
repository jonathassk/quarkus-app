package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.TripChecklistItem;

import java.util.List;
import java.util.Optional;

public class TripChecklistItemRepository implements PanacheRepository<TripChecklistItem> {

    public List<TripChecklistItem> findByTripId(Long tripId) {
        return list("trip.id = ?1 ORDER BY completed ASC, sortOrder ASC, id ASC", tripId);
    }

    public Optional<TripChecklistItem> findByIdAndTripId(Long itemId, Long tripId) {
        return find("id = ?1 AND trip.id = ?2", itemId, tripId).firstResultOptional();
    }

    public int nextSortOrder(Long tripId) {
        Integer max =
                getEntityManager()
                        .createQuery(
                                "SELECT COALESCE(MAX(c.sortOrder), -1) FROM TripChecklistItem c WHERE c.trip.id = :tid",
                                Integer.class)
                        .setParameter("tid", tripId)
                        .getSingleResult();
        return (max != null ? max : -1) + 1;
    }
}
