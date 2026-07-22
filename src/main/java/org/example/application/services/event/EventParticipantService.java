package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.event.InviteParticipantsRequestDTO;
import org.example.application.dto.event.InviteParticipantsResponseDTO;
import org.example.application.dto.event.RsvpRequestDTO;
import org.example.application.exception.event.EventException;
import org.example.domain.entity.User;
import org.example.domain.entity.event.Event;
import org.example.domain.entity.event.EventParticipant;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventVisibility;
import org.example.domain.repository.UserRepository;
import org.example.domain.repository.event.EventParticipantRepository;
import org.example.domain.repository.event.EventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class EventParticipantService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final EventAuthorizationService authorizationService;
    private final EventChatService eventChatService;

    @Transactional
    public List<org.example.application.dto.event.EventParticipantResponseDTO> listParticipants(
            UUID eventId, UUID userId) {
        authorizationService.assertCanView(eventId, userId);
        return participantRepository.findByEventId(eventId).stream()
                .map(
                        p ->
                                org.example.application.dto.event.EventParticipantResponseDTO.builder()
                                        .userId(p.getUser().id)
                                        .fullName(p.getUser().getFullName())
                                        .profilePictureUrl(p.getUser().getProfilePictureUrl())
                                        .role(p.getRole())
                                        .status(p.getStatus())
                                        .build())
                .toList();
    }

    @Transactional
    public InviteParticipantsResponseDTO invite(
            UUID eventId, InviteParticipantsRequestDTO request, UUID userId) {
        Event event = authorizationService.assertCanInvite(eventId, userId);
        if (request == null || request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw EventException.validation("userIds is required");
        }
        return inviteInternal(event, request.getUserIds(), userId);
    }

    @Transactional
    public InviteParticipantsResponseDTO inviteInternal(Event event, List<UUID> userIds, UUID inviterId) {
        List<InviteParticipantsResponseDTO.InvitedUserDTO> invited = new ArrayList<>();
        List<UUID> skipped = new ArrayList<>();
        User inviter = userRepository.findById(inviterId);

        for (UUID targetUserId : userIds) {
            if (targetUserId == null || targetUserId.equals(inviterId)) {
                skipped.add(targetUserId);
                continue;
            }
            if (participantRepository.findByEventAndUser(event.getId(), targetUserId).isPresent()) {
                skipped.add(targetUserId);
                continue;
            }
            User target = userRepository.findById(targetUserId);
            if (target == null) {
                skipped.add(targetUserId);
                continue;
            }

            EventParticipant participant =
                    EventParticipant.builder()
                            .event(event)
                            .user(target)
                            .role(EventParticipantRole.GUEST)
                            .status(EventParticipantStatus.INVITED)
                            .invitedBy(inviter)
                            .build();
            participantRepository.persist(participant);

            invited.add(
                    InviteParticipantsResponseDTO.InvitedUserDTO.builder()
                            .userId(target.id)
                            .fullName(target.getFullName())
                            .status(EventParticipantStatus.INVITED)
                            .role(EventParticipantRole.GUEST)
                            .build());
        }

        return InviteParticipantsResponseDTO.builder().invited(invited).skipped(skipped).build();
    }

    @Transactional
    public EventParticipantResponse rsvp(UUID eventId, RsvpRequestDTO request, UUID userId) {
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            throw EventException.notFound();
        }

        EventParticipant participant =
                participantRepository
                        .findByEventAndUser(eventId, userId)
                        .orElseThrow(EventException::notFound);

        if (participant.getRole() == EventParticipantRole.ORGANIZER) {
            throw EventException.validation("Organizer RSVP is implicit");
        }

        if (request == null
                || request.getStatus() == null
                || (request.getStatus() != EventParticipantStatus.ACCEPTED
                        && request.getStatus() != EventParticipantStatus.DECLINED
                        && request.getStatus() != EventParticipantStatus.MAYBE)) {
            throw EventException.validation("status must be ACCEPTED, DECLINED or MAYBE");
        }

        EventParticipantStatus previous = participant.getStatus();
        participant.setStatus(request.getStatus());
        participant.setRespondedAt(Instant.now());
        participantRepository.persist(participant);

        if (request.getStatus() == EventParticipantStatus.ACCEPTED) {
            eventChatService.onParticipantAccepted(eventId, userId);
        } else if (previous == EventParticipantStatus.ACCEPTED) {
            eventChatService.onParticipantLeft(eventId, userId);
        }

        return new EventParticipantResponse(participant);
    }

    @Transactional
    public void joinPublic(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId);
        if (event == null || event.getVisibility() != EventVisibility.PUBLIC) {
            throw EventException.notFound();
        }
        if (event.getStatus() != org.example.domain.enums.EventStatus.PUBLISHED) {
            throw EventException.notFound();
        }

        if (participantRepository.findByEventAndUser(eventId, userId).isPresent()) {
            return;
        }

        User user = userRepository.findById(userId);
        if (user == null) {
            throw EventException.validation("User not found");
        }

        EventParticipant participant =
                EventParticipant.builder()
                        .event(event)
                        .user(user)
                        .role(EventParticipantRole.GUEST)
                        .status(EventParticipantStatus.ACCEPTED)
                        .respondedAt(Instant.now())
                        .build();
        participantRepository.persist(participant);
        eventChatService.onParticipantAccepted(eventId, userId);
    }

    @Transactional
    public void removeParticipant(UUID eventId, UUID targetUserId, UUID userId) {
        authorizationService.assertCanInvite(eventId, userId);

        EventParticipant participant =
                participantRepository
                        .findByEventAndUser(eventId, targetUserId)
                        .orElseThrow(EventException::notFound);

        if (participant.getRole() == EventParticipantRole.ORGANIZER) {
            throw EventException.validation("Cannot remove organizer");
        }

        if (participant.getStatus() == EventParticipantStatus.ACCEPTED) {
            eventChatService.onParticipantLeft(eventId, targetUserId);
        }

        participantRepository.delete(participant);
    }

    public record EventParticipantResponse(EventParticipant participant) {}
}
