package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.B2bTripLog;
import org.example.domain.enums.B2bTripLogAction;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class B2bTripLogRepository implements PanacheRepository<B2bTripLog> {

    /**
     * Retorna todos os logs de uma viagem específica, do mais recente ao mais antigo.
     */
    public List<B2bTripLog> findByTrip(Long tripId) {
        return list("trip.id = ?1 ORDER BY createdAt DESC", tripId);
    }

    /**
     * Retorna todos os logs de uma agência, do mais recente ao mais antigo.
     * Útil para o dashboard de auditoria do AGENCY_OWNER.
     */
    public List<B2bTripLog> findByAgency(Long agencyId, int maxResults) {
        return getEntityManager()
                .createQuery(
                        "SELECT l FROM B2bTripLog l WHERE l.agency.id = :aid "
                                + "ORDER BY l.createdAt DESC",
                        B2bTripLog.class)
                .setParameter("aid", agencyId)
                .setMaxResults(maxResults)
                .getResultList();
    }

    /**
     * Retorna os logs de um ator específico em uma agência.
     */
    public List<B2bTripLog> findByAgencyAndActor(Long agencyId, Long actorUserId, int maxResults) {
        return getEntityManager()
                .createQuery(
                        "SELECT l FROM B2bTripLog l WHERE l.agency.id = :aid "
                                + "AND l.actorUser.id = :uid ORDER BY l.createdAt DESC",
                        B2bTripLog.class)
                .setParameter("aid", agencyId)
                .setParameter("uid", actorUserId)
                .setMaxResults(maxResults)
                .getResultList();
    }

    /**
     * Retorna os logs de um tipo de ação específico em uma agência.
     */
    public List<B2bTripLog> findByAgencyAndAction(Long agencyId, B2bTripLogAction action, int maxResults) {
        return getEntityManager()
                .createQuery(
                        "SELECT l FROM B2bTripLog l WHERE l.agency.id = :aid "
                                + "AND l.action = :action ORDER BY l.createdAt DESC",
                        B2bTripLog.class)
                .setParameter("aid", agencyId)
                .setParameter("action", action)
                .setMaxResults(maxResults)
                .getResultList();
    }

    /**
     * Retorna logs de uma agência dentro de um período.
     */
    public List<B2bTripLog> findByAgencyBetween(Long agencyId, Instant from, Instant to) {
        return getEntityManager()
                .createQuery(
                        "SELECT l FROM B2bTripLog l WHERE l.agency.id = :aid "
                                + "AND l.createdAt >= :from AND l.createdAt <= :to "
                                + "ORDER BY l.createdAt DESC",
                        B2bTripLog.class)
                .setParameter("aid", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }
}
