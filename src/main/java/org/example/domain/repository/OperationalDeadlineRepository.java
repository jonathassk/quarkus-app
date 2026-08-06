package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.OperationalDeadline;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OperationalDeadlineRepository implements PanacheRepositoryBase<OperationalDeadline, UUID> {

    public List<OperationalDeadline> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY dueAt ASC", tripId);
    }

    public List<OperationalDeadline> findOpenByTripId(UUID tripId) {
        return list("trip.id = ?1 AND completedAt IS NULL ORDER BY dueAt ASC", tripId);
    }
}
