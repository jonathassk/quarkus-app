package org.example.domain.repository.event;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.example.domain.entity.event.EventParticipant;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EventParticipantRepository implements PanacheRepositoryBase<EventParticipant, UUID> {

    public Optional<EventParticipant> findByEventAndUser(UUID eventId, UUID userId) {
        return find("event.id = ?1 and user.id = ?2", eventId, userId).firstResultOptional();
    }

    public List<EventParticipant> findByEventId(UUID eventId) {
        return list("event.id = ?1 order by role asc, invitedAt asc", eventId);
    }

    public long countByEventId(UUID eventId) {
        return count("event.id", eventId);
    }

    public long countAcceptedByEventId(UUID eventId) {
        return count("event.id = ?1 and status = ?2", eventId, EventParticipantStatus.ACCEPTED);
    }

    public boolean isOrganizer(UUID eventId, UUID userId) {
        return count(
                        "event.id = ?1 and user.id = ?2 and role = ?3",
                        eventId,
                        userId,
                        EventParticipantRole.ORGANIZER)
                > 0;
    }

    public boolean isParticipant(UUID eventId, UUID userId) {
        return count("event.id = ?1 and user.id = ?2", eventId, userId) > 0;
    }

    public List<UUID> listAcceptedUserIds(UUID eventId) {
        return getEntityManager()
                .createQuery(
                        "SELECT ep.user.id FROM EventParticipant ep WHERE ep.event.id = :eventId "
                                + "AND (ep.status = :accepted OR ep.role = :organizer)",
                        UUID.class)
                .setParameter("eventId", eventId)
                .setParameter("accepted", EventParticipantStatus.ACCEPTED)
                .setParameter("organizer", EventParticipantRole.ORGANIZER)
                .getResultList();
    }
}
