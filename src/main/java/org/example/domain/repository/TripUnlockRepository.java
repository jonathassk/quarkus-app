package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripUnlock;
import org.example.domain.enums.TripUnlockKind;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TripUnlockRepository implements PanacheRepositoryBase<TripUnlock, UUID> {

    public List<TripUnlock> findByTrip(UUID tripId) {
        return list("trip.id", tripId);
    }

    public boolean exists(UUID tripId, TripUnlockKind kind) {
        return count("trip.id = ?1 and kind = ?2", tripId, kind) > 0;
    }

    /** Kinds liberados por viagem, em uma única query (evita N+1 na listagem). */
    public Map<UUID, Set<TripUnlockKind>> findKindsByTrips(Collection<UUID> tripIds) {
        Map<UUID, Set<TripUnlockKind>> byTrip = new HashMap<>();
        if (tripIds == null || tripIds.isEmpty()) {
            return byTrip;
        }
        List<Object[]> rows = getEntityManager()
                .createQuery(
                        "SELECT u.trip.id, u.kind FROM TripUnlock u WHERE u.trip.id IN :ids",
                        Object[].class)
                .setParameter("ids", tripIds)
                .getResultList();
        for (Object[] row : rows) {
            byTrip.computeIfAbsent((UUID) row[0], k -> EnumSet.noneOf(TripUnlockKind.class))
                    .add((TripUnlockKind) row[1]);
        }
        return byTrip;
    }
}
