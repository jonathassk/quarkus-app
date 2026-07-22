package org.example.application.services.event;

import org.example.domain.entity.User;
import org.example.domain.entity.event.Event;
import org.example.domain.entity.event.EventParticipant;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventStatus;
import org.example.domain.enums.EventVisibility;
import org.example.domain.repository.event.EventParticipantRepository;
import org.example.domain.repository.event.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventAuthorizationServiceTest {

    private EventRepository eventRepository;
    private EventParticipantRepository participantRepository;
    private EventAuthorizationService authorizationService;

    private UUID eventId;
    private UUID organizerUserId;
    private UUID guestUserId;
    private UUID strangerUserId;
    private Event event;
    private User organizer;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        participantRepository = mock(EventParticipantRepository.class);
        authorizationService = new EventAuthorizationService(eventRepository, participantRepository);

        eventId = UUID.randomUUID();
        organizerUserId = UUID.randomUUID();
        guestUserId = UUID.randomUUID();
        strangerUserId = UUID.randomUUID();

        organizer = User.builder().fullName("Organizer").build();
        organizer.id = organizerUserId;

        event =
                Event.builder()
                        .id(eventId)
                        .title("Test")
                        .startAt(Instant.now().plusSeconds(3600))
                        .locationName("Place")
                        .visibility(EventVisibility.PRIVATE)
                        .status(EventStatus.PUBLISHED)
                        .createdBy(organizer)
                        .build();
    }

    @Test
    void organizerCanEdit() {
        when(participantRepository.isOrganizer(eventId, organizerUserId)).thenReturn(true);
        assertTrue(authorizationService.canEdit(event, organizerUserId));
        assertFalse(authorizationService.canEdit(event, guestUserId));
    }

    @Test
    void privateEventStrangerCannotView() {
        when(participantRepository.isParticipant(eventId, strangerUserId)).thenReturn(false);
        assertFalse(authorizationService.canView(event, strangerUserId));
    }

    @Test
    void publicEventAuthenticatedCanView() {
        event.setVisibility(EventVisibility.PUBLIC);
        assertTrue(authorizationService.canView(event, strangerUserId));
    }

    @Test
    void acceptedGuestCanPost() {
        when(participantRepository.isOrganizer(eventId, guestUserId)).thenReturn(false);
        when(participantRepository.findByEventAndUser(eventId, guestUserId))
                .thenReturn(
                        Optional.of(
                                EventParticipant.builder()
                                        .role(EventParticipantRole.GUEST)
                                        .status(EventParticipantStatus.ACCEPTED)
                                        .build()));

        assertTrue(authorizationService.canPost(event, guestUserId));
    }

    @Test
    void invitedGuestCannotPost() {
        when(participantRepository.isOrganizer(eventId, guestUserId)).thenReturn(false);
        when(participantRepository.findByEventAndUser(eventId, guestUserId))
                .thenReturn(
                        Optional.of(
                                EventParticipant.builder()
                                        .role(EventParticipantRole.GUEST)
                                        .status(EventParticipantStatus.INVITED)
                                        .build()));

        assertFalse(authorizationService.canPost(event, guestUserId));
    }
}
