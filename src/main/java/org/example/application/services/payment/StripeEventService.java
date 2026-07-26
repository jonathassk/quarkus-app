package org.example.application.services.payment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.StripeEvent;
import org.example.domain.repository.StripeEventRepository;

/**
 * Controle de idempotência dos webhooks do Stripe.
 *
 * <p>O fluxo é reservar o {@code event.id} antes de processar. Se a reserva falhar,
 * o evento já foi consumido e deve ser respondido com 200 sem reprocessar. Quando o
 * processamento falha, a reserva é liberada para que o retry do Stripe volte a valer.
 */
@Slf4j
@ApplicationScoped
public class StripeEventService {

    @Inject
    StripeEventRepository repository;

    /**
     * Reserva o evento para processamento.
     *
     * @return {@code false} se o evento já havia sido processado antes.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean claim(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            // Sem id não há como deduplicar; processa e registra o risco.
            log.warn("Stripe webhook without event id — idempotency check skipped (type={})", eventType);
            return true;
        }
        if (repository.existsByEventId(eventId)) {
            return false;
        }
        try {
            repository.persistAndFlush(StripeEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType != null ? eventType : "unknown")
                    .build());
            return true;
        } catch (RuntimeException e) {
            // Corrida entre duas entregas do mesmo evento: a UK resolve o empate.
            log.info("Stripe event {} already claimed by a concurrent delivery", eventId);
            return false;
        }
    }

    /** Libera a reserva para que o Stripe possa reentregar o evento. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void release(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            repository.deleteByEventId(eventId);
        } catch (RuntimeException e) {
            log.error("Failed to release Stripe event claim {}: {}", eventId, e.getMessage());
        }
    }
}
