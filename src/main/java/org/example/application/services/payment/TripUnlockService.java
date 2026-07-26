package org.example.application.services.payment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripUnlock;
import org.example.domain.entity.User;
import org.example.domain.enums.TripUnlockKind;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.TripUnlockRepository;
import org.example.domain.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Desbloqueios por viagem gerados pelo pagamento avulso (UNITARIO).
 *
 * <p>O produto atual libera {@link TripUnlockKind#EXPORT_PDF} e
 * {@link TripUnlockKind#AI_GENERATIONS} na mesma compra.
 */
@Slf4j
@ApplicationScoped
public class TripUnlockService {

    /** Benefícios concedidos por uma compra UNITARIO. */
    public static final Set<TripUnlockKind> UNITARIO_KINDS =
            EnumSet.of(TripUnlockKind.EXPORT_PDF, TripUnlockKind.AI_GENERATIONS);

    @Inject
    TripUnlockRepository unlockRepository;

    @Inject
    TripRepository tripRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Concede os benefícios do pagamento avulso à viagem, ignorando os que já existem.
     */
    @Transactional
    public void grantUnitario(UUID tripId, UUID userId, String stripeSessionId, BigDecimal amount, String currency) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            log.warn("Trip {} not found — UNITARIO unlock skipped (session={})", tripId, stripeSessionId);
            return;
        }
        User user = userId != null ? userRepository.findById(userId) : null;

        for (TripUnlockKind kind : UNITARIO_KINDS) {
            if (unlockRepository.exists(tripId, kind)) {
                log.info("Trip {} already unlocked for {} — skipping", tripId, kind);
                continue;
            }
            unlockRepository.persist(TripUnlock.builder()
                    .trip(trip)
                    .user(user)
                    .kind(kind)
                    .amount(amount)
                    .currency(currency)
                    .stripeSessionId(stripeSessionId)
                    .build());
            log.info("Trip {} unlocked for {} (session={})", tripId, kind, stripeSessionId);
        }
    }

    public boolean hasUnlock(UUID tripId, TripUnlockKind kind) {
        return unlockRepository.exists(tripId, kind);
    }

    /** Kinds já liberados na viagem. */
    public Set<TripUnlockKind> listKinds(UUID tripId) {
        Set<TripUnlockKind> kinds = EnumSet.noneOf(TripUnlockKind.class);
        unlockRepository.findByTrip(tripId).forEach(u -> kinds.add(u.getKind()));
        return kinds;
    }

    /** Kinds liberados para várias viagens de uma vez. */
    public Map<UUID, Set<TripUnlockKind>> listKindsByTrips(Collection<UUID> tripIds) {
        return unlockRepository.findKindsByTrips(tripIds);
    }
}
