package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.event.*;
import org.example.application.exception.event.EventException;
import org.example.domain.entity.User;
import org.example.domain.entity.event.Event;
import org.example.domain.entity.event.EventParticipant;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventStatus;
import org.example.domain.enums.EventVisibility;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.event.EventParticipantRepository;
import org.example.domain.repository.event.EventRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final EventAuthorizationService authorizationService;
    private final EventParticipantService participantService;
    private final EventMapper eventMapper;
    private final EventValidationUtils validationUtils;
    private final EventChatService eventChatService;

    @ConfigProperty(name = "events.enabled", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty(name = "events.public-page-size", defaultValue = "20")
    int publicPageSize;

    public void assertEnabled() {
        if (!eventsEnabled) {
            throw EventException.disabled();
        }
    }

    @Transactional
    public EventResponseDTO create(CreateEventRequestDTO request, UUID userId) {
        assertEnabled();
        validateCreateRequest(request);

        User creator = userRepository.findById(userId);
        if (creator == null) {
            throw EventException.validation("User not found");
        }

        if (request.getSource() != null) {
            validateSource(request.getSource(), userId);
        }

        Event event =
                Event.builder()
                        .title(validationUtils.sanitizeText(request.getTitle(), 200))
                        .description(
                                request.getDescription() != null
                                        ? validationUtils.sanitizeText(request.getDescription(), 5000)
                                        : null)
                        .startAt(request.getStartAt())
                        .endAt(request.getEndAt())
                        .locationName(validationUtils.sanitizeText(request.getLocation().getName(), 300))
                        .locationAddress(request.getLocation().getAddress())
                        .locationCity(request.getLocation().getCity())
                        .locationCountry(request.getLocation().getCountry())
                        .locationLatitude(request.getLocation().getLatitude())
                        .locationLongitude(request.getLocation().getLongitude())
                        .visibility(request.getVisibility() != null ? request.getVisibility() : EventVisibility.PRIVATE)
                        .status(EventStatus.PUBLISHED)
                        .coverImageUrl(request.getCoverImageUrl())
                        .createdBy(creator)
                        .build();

        if (request.getSource() != null) {
            event.setSourceTripId(request.getSource().getTripId());
            event.setSourceSegmentIndex(request.getSource().getSegmentIndex());
            event.setSourceActivityId(request.getSource().getActivityId());
        }

        if (event.getCoverImageUrl() != null) {
            validationUtils.validateImageUrl(event.getCoverImageUrl());
        }

        eventRepository.persist(event);

        EventParticipant organizer =
                EventParticipant.builder()
                        .event(event)
                        .user(creator)
                        .role(EventParticipantRole.ORGANIZER)
                        .status(EventParticipantStatus.ACCEPTED)
                        .invitedBy(creator)
                        .build();
        participantRepository.persist(organizer);

        if (request.getInviteUserIds() != null && !request.getInviteUserIds().isEmpty()) {
            participantService.inviteInternal(event, request.getInviteUserIds(), userId);
        }

        return eventMapper.toResponse(event, userId);
    }

    @Transactional
    public EventResponseDTO get(UUID eventId, UUID userId) {
        assertEnabled();
        Event event = authorizationService.assertCanView(eventId, userId);
        refreshCompletedIfNeeded(event);
        return eventMapper.toResponse(event, userId);
    }

    @Transactional
    public EventResponseDTO update(UUID eventId, UpdateEventRequestDTO request, UUID userId) {
        assertEnabled();
        Event event = authorizationService.assertCanEdit(eventId, userId);

        if (request.getTitle() != null) {
            event.setTitle(validationUtils.sanitizeText(request.getTitle(), 200));
        }
        if (request.getDescription() != null) {
            event.setDescription(validationUtils.sanitizeText(request.getDescription(), 5000));
        }
        if (request.getStartAt() != null) {
            event.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            event.setEndAt(request.getEndAt());
        }
        if (request.getVisibility() != null) {
            event.setVisibility(request.getVisibility());
        }
        if (request.getCoverImageUrl() != null) {
            validationUtils.validateImageUrl(request.getCoverImageUrl());
            event.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.getLocation() != null) {
            if (request.getLocation().getName() != null) {
                event.setLocationName(validationUtils.sanitizeText(request.getLocation().getName(), 300));
            }
            if (request.getLocation().getAddress() != null) {
                event.setLocationAddress(request.getLocation().getAddress());
            }
            if (request.getLocation().getCity() != null) {
                event.setLocationCity(request.getLocation().getCity());
            }
            if (request.getLocation().getCountry() != null) {
                event.setLocationCountry(request.getLocation().getCountry());
            }
            if (request.getLocation().getLatitude() != null) {
                event.setLocationLatitude(request.getLocation().getLatitude());
            }
            if (request.getLocation().getLongitude() != null) {
                event.setLocationLongitude(request.getLocation().getLongitude());
            }
        }

        if (event.getEndAt() != null && !event.getEndAt().isAfter(event.getStartAt())) {
            throw EventException.validation("endAt must be after startAt");
        }

        eventRepository.persist(event);
        return eventMapper.toResponse(event, userId);
    }

    @Transactional
    public void cancel(UUID eventId, UUID userId) {
        assertEnabled();
        Event event = authorizationService.assertCanEdit(eventId, userId);
        event.setStatus(EventStatus.CANCELLED);
        event.setCancelledAt(Instant.now());
        eventRepository.persist(event);
        eventChatService.archiveConversation(eventId);
    }

    @Transactional
    public EventListResponseDTO listMine(UUID userId) {
        assertEnabled();
        eventRepository.markCompletedPastEvents(Instant.now());

        List<EventResponseDTO> organizing =
                eventRepository.findOrganizing(userId).stream()
                        .map(e -> eventMapper.toResponse(e, userId))
                        .collect(Collectors.toList());

        List<EventResponseDTO> participating =
                eventRepository.findParticipating(userId).stream()
                        .map(e -> eventMapper.toResponse(e, userId))
                        .collect(Collectors.toList());

        List<EventResponseDTO> pendingInvites =
                eventRepository.findPendingInvites(userId).stream()
                        .map(e -> eventMapper.toResponse(e, userId))
                        .collect(Collectors.toList());

        return EventListResponseDTO.builder()
                .organizing(organizing)
                .participating(participating)
                .pendingInvites(pendingInvites)
                .build();
    }

    @Transactional
    public PublicEventsPageDTO listPublic(String city, Instant from, Instant to, String cursor, Integer limit) {
        assertEnabled();
        eventRepository.markCompletedPastEvents(Instant.now());

        int pageSize = limit != null && limit > 0 ? Math.min(limit, publicPageSize) : publicPageSize;
        EventMapper.Cursor decoded = eventMapper.decodeCursor(cursor);

        List<Event> events =
                eventRepository.findPublicEvents(
                        city,
                        from,
                        to,
                        pageSize + 1,
                        decoded != null ? decoded.timestamp() : null,
                        decoded != null ? decoded.id() : null);

        String nextToken = null;
        if (events.size() > pageSize) {
            Event last = events.get(pageSize - 1);
            nextToken = eventMapper.encodeCursor(last.getStartAt(), last.getId());
            events = events.subList(0, pageSize);
        }

        List<EventResponseDTO> items =
                events.stream().map(eventMapper::toPublicResponse).collect(Collectors.toList());

        return PublicEventsPageDTO.builder().items(items).nextToken(nextToken).build();
    }

    @Transactional
    public EventResponseDTO getBySource(UUID tripId, String activityId, UUID userId) {
        assertEnabled();
        Event event =
                eventRepository
                        .findBySource(tripId, activityId)
                        .orElseThrow(EventException::notFound);
        return get(event.getId(), userId);
    }

    private void validateCreateRequest(CreateEventRequestDTO request) {
        if (request == null
                || request.getTitle() == null
                || request.getTitle().isBlank()
                || request.getStartAt() == null
                || request.getLocation() == null
                || request.getLocation().getName() == null
                || request.getLocation().getName().isBlank()) {
            throw EventException.validation("title, startAt and location.name are required");
        }
        if (request.getEndAt() != null && !request.getEndAt().isAfter(request.getStartAt())) {
            throw EventException.validation("endAt must be after startAt");
        }
    }

    private void validateSource(EventSourceDTO source, UUID userId) {
        if (source.getTripId() == null || source.getActivityId() == null || source.getActivityId().isBlank()) {
            throw EventException.validation("source.tripId and source.activityId are required when source is provided");
        }
        if (!tripRepository.isUserLinkedToTrip(source.getTripId(), userId)) {
            throw EventException.notFound();
        }
        eventRepository
                .findBySource(source.getTripId(), source.getActivityId())
                .ifPresent(
                        existing ->
                                {
                                    throw EventException.alreadyExists(existing.getId().toString());
                                });
    }

    private void refreshCompletedIfNeeded(Event event) {
        if (event.getStatus() == EventStatus.PUBLISHED
                && event.getEndAt() != null
                && event.getEndAt().isBefore(Instant.now())) {
            event.setStatus(EventStatus.COMPLETED);
            eventRepository.persist(event);
        }
    }
}
