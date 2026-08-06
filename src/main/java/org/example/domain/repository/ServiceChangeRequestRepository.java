package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.ServiceChangeRequest;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ServiceChangeRequestRepository implements PanacheRepositoryBase<ServiceChangeRequest, UUID> {

    public List<ServiceChangeRequest> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY createdAt DESC", tripId);
    }

    public List<ServiceChangeRequest> findByServiceId(UUID serviceId) {
        return list("service.id = ?1 ORDER BY createdAt DESC", serviceId);
    }
}
