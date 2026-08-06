package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.TripPayment;
import org.example.domain.enums.TripPaymentKind;
import org.example.domain.enums.TripPaymentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripPaymentRepository implements PanacheRepositoryBase<TripPayment, UUID> {

    public List<TripPayment> findByTripId(UUID tripId) {
        return list("trip.id = ?1 ORDER BY createdAt DESC", tripId);
    }

    public Optional<TripPayment> findByStripeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return find("stripeSessionId", sessionId.trim()).firstResultOptional();
    }

    /** Último PENDING com session Stripe — para reuso em double-submit. */
    public Optional<TripPayment> findLatestPendingWithSession(UUID tripId, TripPaymentKind kind) {
        return find(
                        "trip.id = ?1 AND kind = ?2 AND status = ?3 AND stripeSessionId IS NOT NULL "
                                + "ORDER BY createdAt DESC",
                        tripId,
                        kind,
                        TripPaymentStatus.PENDING)
                .firstResultOptional();
    }

    public BigDecimal sumPaidByTrip(UUID tripId) {
        BigDecimal sum = getEntityManager()
                .createQuery(
                        "SELECT COALESCE(SUM(p.amount), 0) FROM TripPayment p "
                                + "WHERE p.trip.id = :tid AND p.status = :st",
                        BigDecimal.class)
                .setParameter("tid", tripId)
                .setParameter("st", TripPaymentStatus.PAID)
                .getSingleResult();
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public boolean hasPaidKind(UUID tripId, TripPaymentKind kind) {
        return count("trip.id = ?1 AND kind = ?2 AND status = ?3",
                tripId, kind, TripPaymentStatus.PAID) > 0;
    }
}
