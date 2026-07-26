package org.example.application.services.trip;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.CreateTripCommentRequest;
import org.example.application.dto.trip.response.TripCommentDTO;
import org.example.application.dto.trip.response.TripCommentsPageDTO;
import org.example.application.services.TripCollaborationService;
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripComment;
import org.example.domain.entity.TripCommentRead;
import org.example.domain.entity.User;
import org.example.domain.enums.NotificationKind;
import org.example.domain.enums.TripCommentTargetType;
import org.example.domain.enums.UserPermissionLevel;
import org.example.domain.repository.TripCommentReadRepository;
import org.example.domain.repository.TripCommentRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripCommentService {

    private static final int MAX_BODY = 2000;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    private final TripRepository tripRepository;
    private final TripCommentRepository commentRepository;
    private final TripCommentReadRepository readRepository;
    private final UserRepository userRepository;
    private final TripCollaborationService collaborationService;
    private final NotificationService notificationService;

    @Transactional
    public TripCommentsPageDTO list(
            UUID tripId,
            UUID userId,
            TripCommentTargetType targetType,
            String targetId,
            boolean markRead) {
        Trip trip = requireMemberTrip(tripId, userId);
        Instant lastRead =
                readRepository
                        .findByTripAndUser(tripId, userId)
                        .map(TripCommentRead::getLastReadAt)
                        .orElse(null);
        long unread = commentRepository.countUnread(tripId, userId, lastRead);

        List<TripComment> comments;
        if (targetType != null) {
            comments = commentRepository.findActiveByTripAndTarget(tripId, targetType, targetId);
        } else {
            comments = commentRepository.findActiveByTripId(tripId);
        }

        if (markRead) {
            markRead(trip.id, userId);
            unread = 0;
        }

        return TripCommentsPageDTO.builder()
                .items(comments.stream().map(this::toDto).collect(Collectors.toList()))
                .unreadCount(unread)
                .build();
    }

    public long unreadCount(UUID tripId, UUID userId) {
        requireMemberTrip(tripId, userId);
        Instant lastRead =
                readRepository
                        .findByTripAndUser(tripId, userId)
                        .map(TripCommentRead::getLastReadAt)
                        .orElse(null);
        return commentRepository.countUnread(tripId, userId, lastRead);
    }

    @Transactional
    public TripCommentDTO create(UUID tripId, UUID userId, CreateTripCommentRequest request) {
        Trip trip = requireMemberTrip(tripId, userId);
        if (request == null || request.getBody() == null || request.getBody().isBlank()) {
            throw new BadRequestException("body is required");
        }
        TripCommentTargetType targetType =
                request.getTargetType() != null ? request.getTargetType() : TripCommentTargetType.TRIP;
        if (targetType != TripCommentTargetType.TRIP
                && (request.getTargetId() == null || request.getTargetId().isBlank())) {
            throw new BadRequestException("targetId is required for targetType " + targetType);
        }

        String body = sanitize(request.getBody());
        User author = userRepository.findById(userId);
        TripComment comment =
                TripComment.builder()
                        .trip(trip)
                        .targetType(targetType)
                        .targetId(
                                request.getTargetId() != null && !request.getTargetId().isBlank()
                                        ? request.getTargetId().trim()
                                        : null)
                        .author(author)
                        .body(body)
                        .build();
        commentRepository.persist(comment);
        // Autor já "leu" o próprio comentário
        markRead(tripId, userId);
        notifyTripMembersOfComment(trip, userId, author, body);
        log.info("Created trip comment tripId={} commentId={} by={}", tripId, comment.id, userId);
        return toDto(comment);
    }

    private void notifyTripMembersOfComment(Trip trip, UUID authorId, User author, String body) {
        List<UUID> recipients =
                tripRepository.listTripMemberUserIds(trip.id).stream()
                        .filter(id -> !id.equals(authorId))
                        .toList();
        if (recipients.isEmpty()) {
            return;
        }
        String authorName =
                author != null && author.getFullName() != null ? author.getFullName() : "Alguém";
        String tripName = trip.getName() != null ? trip.getName() : "viagem";
        String preview = body.length() > 120 ? body.substring(0, 117) + "..." : body;
        notificationService.createForUsers(
                recipients,
                NotificationKind.TRIP_COMMENT,
                authorName + " comentou em \"" + tripName + "\"",
                preview,
                "TRIP",
                trip.id,
                true);
    }

    @Transactional
    public TripCommentDTO resolve(UUID tripId, UUID commentId, UUID userId) {
        Trip trip = requireMemberTrip(tripId, userId);
        UserPermissionLevel level = collaborationService.resolvePermission(trip, userId);
        if (level == null || !level.canEdit()) {
            throw new ForbiddenException("Only ADMIN or OWNER can resolve comments");
        }
        TripComment comment =
                commentRepository
                        .findActiveById(commentId)
                        .filter(c -> c.getTrip() != null && tripId.equals(c.getTrip().id))
                        .orElseThrow(() -> new NotFoundException("Comment not found"));
        if (comment.getResolvedAt() == null) {
            comment.setResolvedAt(Instant.now());
            commentRepository.persist(comment);
        }
        return toDto(comment);
    }

    @Transactional
    public void delete(UUID tripId, UUID commentId, UUID userId) {
        Trip trip = requireMemberTrip(tripId, userId);
        TripComment comment =
                commentRepository
                        .findActiveById(commentId)
                        .filter(c -> c.getTrip() != null && tripId.equals(c.getTrip().id))
                        .orElseThrow(() -> new NotFoundException("Comment not found"));

        boolean isAuthor = comment.getAuthor() != null && comment.getAuthor().id.equals(userId);
        UserPermissionLevel level = collaborationService.resolvePermission(trip, userId);
        boolean isAdmin = level != null && level.canEdit();
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("You cannot delete this comment");
        }
        comment.setDeletedAt(Instant.now());
        commentRepository.persist(comment);
    }

    @Transactional
    public void markRead(UUID tripId, UUID userId) {
        Instant now = Instant.now();
        TripCommentRead read =
                readRepository
                        .findByTripAndUser(tripId, userId)
                        .orElseGet(
                                () ->
                                        TripCommentRead.builder()
                                                .tripId(tripId)
                                                .userId(userId)
                                                .build());
        read.setLastReadAt(now);
        readRepository.persist(read);
    }

    private Trip requireMemberTrip(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        collaborationService.requireMember(trip, userId);
        return trip;
    }

    private String sanitize(String text) {
        String cleaned = HTML_TAG.matcher(text).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            throw new BadRequestException("body is required");
        }
        if (cleaned.length() > MAX_BODY) {
            throw new BadRequestException("body exceeds maximum length of " + MAX_BODY);
        }
        return cleaned;
    }

    private TripCommentDTO toDto(TripComment comment) {
        User author = comment.getAuthor();
        return TripCommentDTO.builder()
                .id(comment.id)
                .tripId(comment.getTrip() != null ? comment.getTrip().id : null)
                .targetType(comment.getTargetType())
                .targetId(comment.getTargetId())
                .authorId(author != null ? author.id : null)
                .authorName(author != null ? author.getFullName() : null)
                .body(comment.getBody())
                .resolvedAt(comment.getResolvedAt())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
