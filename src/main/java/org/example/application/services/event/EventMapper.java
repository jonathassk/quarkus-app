package org.example.application.services.event;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.event.*;
import org.example.domain.entity.User;
import org.example.domain.entity.event.Event;
import org.example.domain.entity.event.EventParticipant;
import org.example.domain.enums.EventParticipantRole;
import org.example.domain.enums.EventParticipantStatus;
import org.example.domain.enums.EventStatus;
import org.example.domain.repository.event.EventParticipantRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class EventMapper {

    private final EventParticipantRepository participantRepository;

    public EventResponseDTO toResponse(Event event, UUID viewerUserId) {
        EventParticipant viewerParticipant =
                participantRepository.findByEventAndUser(event.getId(), viewerUserId).orElse(null);

        EventParticipantRole myRole = viewerParticipant != null ? viewerParticipant.getRole() : null;
        EventParticipantStatus myStatus = viewerParticipant != null ? viewerParticipant.getStatus() : null;
        boolean isOrganizer = myRole == EventParticipantRole.ORGANIZER;

        return EventResponseDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .location(toLocation(event))
                .visibility(event.getVisibility())
                .status(resolveDisplayStatus(event))
                .coverImageUrl(event.getCoverImageUrl())
                .createdBy(event.getCreatedBy() != null ? event.getCreatedBy().id : null)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .source(toSource(event))
                .participantCount((int) participantRepository.countByEventId(event.getId()))
                .myRole(myRole)
                .myStatus(myStatus)
                .canEdit(isOrganizer && event.getStatus() != EventStatus.CANCELLED)
                .canInvite(isOrganizer && event.getStatus() == EventStatus.PUBLISHED)
                .build();
    }

    public EventResponseDTO toPublicResponse(Event event) {
        return EventResponseDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .location(toLocation(event))
                .visibility(event.getVisibility())
                .status(resolveDisplayStatus(event))
                .coverImageUrl(event.getCoverImageUrl())
                .createdBy(event.getCreatedBy() != null ? event.getCreatedBy().id : null)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .source(toSource(event))
                .participantCount((int) participantRepository.countByEventId(event.getId()))
                .build();
    }

    public EventParticipantResponseDTO toParticipantResponse(EventParticipant participant) {
        User user = participant.getUser();
        return EventParticipantResponseDTO.builder()
                .userId(user.id)
                .fullName(user.getFullName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .role(participant.getRole())
                .status(participant.getStatus())
                .build();
    }

    public EventLocationDTO toLocation(Event event) {
        return EventLocationDTO.builder()
                .name(event.getLocationName())
                .address(event.getLocationAddress())
                .city(event.getLocationCity())
                .country(event.getLocationCountry())
                .latitude(event.getLocationLatitude())
                .longitude(event.getLocationLongitude())
                .build();
    }

    public EventSourceDTO toSource(Event event) {
        if (event.getSourceTripId() == null && event.getSourceActivityId() == null) {
            return null;
        }
        return EventSourceDTO.builder()
                .tripId(event.getSourceTripId())
                .segmentIndex(event.getSourceSegmentIndex())
                .activityId(event.getSourceActivityId())
                .build();
    }

    public EventStatus resolveDisplayStatus(Event event) {
        if (event.getStatus() == EventStatus.PUBLISHED
                && event.getEndAt() != null
                && event.getEndAt().isBefore(Instant.now())) {
            return EventStatus.COMPLETED;
        }
        return event.getStatus();
    }

    public String encodeCursor(Instant timestamp, UUID id) {
        String raw = timestamp.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decodeCursor(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep < 0) {
                return null;
            }
            return new Cursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (Exception e) {
            return null;
        }
    }

    public record Cursor(Instant timestamp, UUID id) {}
}
