package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.example.application.exception.event.EventException;
import org.example.domain.entity.event.Event;
import org.example.domain.entity.event.EventParticipant;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventStatus;
import org.example.domain.enums.EventVisibility;
import org.example.domain.repository.event.EventParticipantRepository;
import org.example.domain.repository.event.EventRepository;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class EventAuthorizationService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;

    public Event assertCanView(UUID eventId, UUID userId) {
        Event event = requireEvent(eventId);
        if (!canView(event, userId)) {
            throw EventException.notFound();
        }
        return event;
    }

    public Event assertCanEdit(UUID eventId, UUID userId) {
        Event event = requireEvent(eventId);
        if (!canEdit(event, userId)) {
            throw EventException.notFound();
        }
        return event;
    }

    public Event assertCanInvite(UUID eventId, UUID userId) {
        Event event = requireEvent(eventId);
        if (!canInvite(event, userId)) {
            throw EventException.notFound();
        }
        return event;
    }

    public Event assertCanPost(UUID eventId, UUID userId) {
        Event event = requireEvent(eventId);
        if (!canPost(event, userId)) {
            throw EventException.notFound();
        }
        return event;
    }

    public Event assertCanChat(UUID eventId, UUID userId) {
        Event event = requireEvent(eventId);
        if (!canChat(event, userId)) {
            throw EventException.notFound();
        }
        return event;
    }

    public boolean canView(Event event, UUID userId) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return isOrganizer(event, userId) || wasParticipant(event.getId(), userId);
        }
        if (event.getVisibility() == EventVisibility.PUBLIC) {
            return true;
        }
        return isParticipant(event.getId(), userId);
    }

    public boolean canEdit(Event event, UUID userId) {
        return isOrganizer(event, userId) && event.getStatus() != EventStatus.CANCELLED;
    }

    public boolean canInvite(Event event, UUID userId) {
        return isOrganizer(event, userId) && event.getStatus() == EventStatus.PUBLISHED;
    }

    public boolean canPost(Event event, UUID userId) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return false;
        }
        if (isOrganizer(event, userId)) {
            return true;
        }
        return participantRepository
                .findByEventAndUser(event.getId(), userId)
                .map(p -> p.getStatus() == EventParticipantStatus.ACCEPTED)
                .orElse(false);
    }

    public boolean canChat(Event event, UUID userId) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return false;
        }
        if (isOrganizer(event, userId)) {
            return true;
        }
        return participantRepository
                .findByEventAndUser(event.getId(), userId)
                .map(p -> p.getStatus() == EventParticipantStatus.ACCEPTED)
                .orElse(false);
    }

    private boolean isOrganizer(Event event, UUID userId) {
        return event.getCreatedBy() != null
                && event.getCreatedBy().id.equals(userId)
                && participantRepository.isOrganizer(event.getId(), userId);
    }

    private boolean isParticipant(UUID eventId, UUID userId) {
        return participantRepository.isParticipant(eventId, userId);
    }

    private boolean wasParticipant(UUID eventId, UUID userId) {
        return participantRepository.isParticipant(eventId, userId);
    }

    private Event requireEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            throw EventException.notFound();
        }
        return event;
    }
}
