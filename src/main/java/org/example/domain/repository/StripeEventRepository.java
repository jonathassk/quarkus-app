package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.StripeEvent;

import java.util.UUID;

@ApplicationScoped
public class StripeEventRepository implements PanacheRepositoryBase<StripeEvent, UUID> {

    public boolean existsByEventId(String eventId) {
        return count("eventId", eventId) > 0;
    }

    public long deleteByEventId(String eventId) {
        return delete("eventId", eventId);
    }
}
