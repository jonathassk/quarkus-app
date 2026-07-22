package org.example.application.services;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Agency;
import org.example.domain.entity.B2bTripLog;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.repository.B2bTripLogRepository;

/**
 * Serviço de auditoria operacional B2B.
 *
 * <p>Registra cada operação mutável em uma viagem de agência na tabela
 * {@code b2b_trip_logs}. O AGENCY_OWNER pode consultar o histórico completo
 * para auditar erros de logística ou ações indevidas de consultores.
 *
 * <p><strong>Uso:</strong> Injete este serviço nos controllers/services que
 * modificam entidades de viagem e chame {@link #record} após cada operação.
 *
 * <pre>{@code
 * // Exemplo no TripController após atualizar nome/descrição:
 * auditService.record(
 *     trip,
 *     actorUserId,
 *     B2bTripLogAction.TRIP_UPDATED,
 *     "TRIP", trip.id,
 *     "{\"name\": \"Nome Antigo\"}",
 *     "{\"name\": \"Nome Novo\"}",
 *     "Nome da viagem alterado de 'Nome Antigo' para 'Nome Novo'",
 *     ipAddress
 * );
 * }</pre>
 */
@Slf4j
@ApplicationScoped
public class B2bAuditService {

    @Inject
    B2bTripLogRepository logRepository;

    @Inject
    org.example.domain.repository.UserRepository userRepository;

    /**
     * Registra uma entrada de auditoria para uma viagem de agência.
     *
     * <p>Se a viagem não tiver {@code agency} definida, o registro é silenciosamente
     * ignorado — viagens B2C não são auditadas por este serviço.
     *
     * @param trip             Viagem auditada.
     * @param actorUserId      ID do usuário que executou a operação.
     * @param action           Tipo de ação realizada.
     * @param entityType       Tipo da entidade-alvo (ex.: "SEGMENT", "ACTIVITY").
     * @param entityId         ID da entidade-alvo, ou {@code null} se não aplicável.
     * @param previousSnapshot JSON com o estado anterior à operação, ou {@code null}.
     * @param newSnapshot      JSON com o estado posterior à operação, ou {@code null}.
     * @param description      Texto legível da operação para exibição no painel.
     * @param ipAddress        IP de origem da requisição, ou {@code null}.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(
            Trip trip,
            UUID actorUserId,
            B2bTripLogAction action,
            String entityType,
            UUID entityId,
            String previousSnapshot,
            String newSnapshot,
            String description,
            String ipAddress) {

        if (trip == null || trip.getAgency() == null) {
            // Viagem B2C — auditoria B2B não aplicável
            return;
        }

        try {
            User actor = userRepository.findById(actorUserId);
            if (actor == null) {
                log.warn("B2B audit: actor userId={} not found, skipping log for tripId={}", actorUserId, trip.id);
                return;
            }

            Agency agency = trip.getAgency();

            B2bTripLog entry = B2bTripLog.builder()
                    .agency(agency)
                    .trip(trip)
                    .actorUser(actor)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .previousSnapshot(previousSnapshot)
                    .newSnapshot(newSnapshot)
                    .description(description != null ? truncate(description, 500) : null)
                    .ipAddress(ipAddress != null ? truncate(ipAddress, 45) : null)
                    .build();

            logRepository.persist(entry);

            log.debug(
                    "B2B audit: action={} agencyId={} tripId={} actor={} entity={}#{}",
                    action, agency.id, trip.id, actorUserId, entityType, entityId);

        } catch (Exception e) {
            // Auditoria NUNCA deve bloquear a operação principal
            log.error("B2B audit failed (non-blocking): tripId={} action={} actor={}",
                    trip.id, action, actorUserId, e);
        }
    }

    /**
     * Sobrecarga simplificada para operações sem snapshot de estado.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(Trip trip, UUID actorUserId, B2bTripLogAction action, String description) {
        record(trip, actorUserId, action, "TRIP", trip != null ? trip.id : null,
                null, null, description, null);
    }

    /**
     * Sobrecarga para operações em sub-entidades sem snapshot.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(
            Trip trip,
            UUID actorUserId,
            B2bTripLogAction action,
            String entityType,
            UUID entityId,
            String description) {
        record(trip, actorUserId, action, entityType, entityId, null, null, description, null);
    }

    // -------------------------------------------------------------------------

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}
