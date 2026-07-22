package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import org.example.domain.entity.TripChecklistItem;

import java.util.List;
import java.util.Optional;

public class TripChecklistItemRepository implements PanacheRepositoryBase<TripChecklistItem, UUID> {

    public List<TripChecklistItem> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY completed ASC, sortOrder ASC, id ASC", tripId);
    }

    public Optional<TripChecklistItem> findByIdAndTripId(UUID itemId, UUID tripId) {
        return find("id = ?1 AND trip.id = ?2", itemId, tripId).firstResultOptional();
    }

    public int nextSortOrder(UUID tripId) {
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
