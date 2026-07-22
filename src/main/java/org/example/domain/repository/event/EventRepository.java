package org.example.domain.repository.event;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.event.Event;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventStatus;
import org.example.domain.enums.EventVisibility;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EventRepository implements PanacheRepositoryBase<Event, UUID> {

    public Optional<Event> findBySource(UUID tripId, String activityId) {
        if (tripId == null || activityId == null || activityId.isBlank()) {
            return Optional.empty();
        }
        return find("sourceTripId = ?1 and sourceActivityId = ?2", tripId, activityId)
                .firstResultOptional();
    }

    public List<Event> findOrganizing(UUID userId) {
        return list(
                "createdBy.id = ?1 and status != ?2 order by startAt asc",
                userId,
                EventStatus.CANCELLED);
    }

    public List<Event> findParticipating(UUID userId) {
        return getEntityManager()
                .createQuery(
                        "SELECT e FROM Event e JOIN EventParticipant ep ON ep.event = e "
                                + "WHERE ep.user.id = :uid AND ep.role != :organizer "
                                + "AND e.status != :cancelled ORDER BY e.startAt ASC",
                        Event.class)
                .setParameter("uid", userId)
                .setParameter("organizer", EventParticipantRole.ORGANIZER)
                .setParameter("cancelled", EventStatus.CANCELLED)
                .getResultList();
    }

    public List<Event> findPendingInvites(UUID userId) {
        return getEntityManager()
                .createQuery(
                        "SELECT e FROM Event e JOIN EventParticipant ep ON ep.event = e "
                                + "WHERE ep.user.id = :uid AND ep.status = :invited "
                                + "AND e.status = :published ORDER BY e.startAt ASC",
                        Event.class)
                .setParameter("uid", userId)
                .setParameter("invited", EventParticipantStatus.INVITED)
                .setParameter("published", EventStatus.PUBLISHED)
                .getResultList();
    }

    public List<Event> findPublicEvents(
            String city, Instant from, Instant to, int limit, Instant cursorStartAt, UUID cursorId) {
        StringBuilder jpql =
                new StringBuilder(
                        "SELECT e FROM Event e WHERE e.visibility = :visibility AND e.status = :status");
        if (city != null && !city.isBlank()) {
            jpql.append(" AND LOWER(e.locationCity) = LOWER(:city)");
        }
        if (from != null) {
            jpql.append(" AND e.startAt >= :from");
        }
        if (to != null) {
            jpql.append(" AND e.startAt <= :to");
        }
        if (cursorStartAt != null && cursorId != null) {
            jpql.append(" AND (e.startAt > :cursorStartAt OR (e.startAt = :cursorStartAt AND e.id > :cursorId))");
        }
        jpql.append(" ORDER BY e.startAt ASC, e.id ASC");

        var query = getEntityManager().createQuery(jpql.toString(), Event.class);
        query.setParameter("visibility", EventVisibility.PUBLIC);
        query.setParameter("status", EventStatus.PUBLISHED);
        if (city != null && !city.isBlank()) {
            query.setParameter("city", city.trim());
        }
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        if (cursorStartAt != null && cursorId != null) {
            query.setParameter("cursorStartAt", cursorStartAt);
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public int markCompletedPastEvents(Instant now) {
        return update(
                "status = ?1 where status = ?2 and endAt is not null and endAt < ?3",
                EventStatus.COMPLETED,
                EventStatus.PUBLISHED,
                now);
    }
}
