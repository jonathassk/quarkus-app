package org.example.application.services;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.TripUserDTO;
import org.example.application.dto.trip.request.ShareTripRequestDTO;
import org.example.application.dto.trip.request.ShareTripUserItemDTO;
import org.example.application.dto.trip.request.UpdateSharePermissionDTO;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripUser;
import org.example.domain.entity.User;
import org.example.domain.enums.UserPermissionLevel;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.application.services.chat.TripChatService;
import org.example.infrastructure.mapper.TripMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripCollaborationService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripChatService tripChatService;

    public UserPermissionLevel resolvePermission(Trip trip, UUID userId) {
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) {
            return UserPermissionLevel.OWNER;
        }
        return tripRepository
                .findTripUser(trip.id, userId)
                .map(tu -> UserPermissionLevel.fromString(tu.getPermissionLevel()))
                .orElse(null);
    }

    public void requireMember(Trip trip, UUID userId) {
        if (!tripRepository.isUserLinkedToTrip(trip.id, userId)) {
            throw new ForbiddenException("You do not have access to this trip");
        }
    }

    public void requireCanManageMembers(Trip trip, UUID actorId) {
        requireMember(trip, actorId);
        UserPermissionLevel level = resolvePermission(trip, actorId);
        if (level == null || !level.canManageUsers()) {
            throw new ForbiddenException("Only the trip owner can manage collaborators");
        }
    }

    public void requireCanEdit(Trip trip, UUID actorId) {
        requireMember(trip, actorId);
        UserPermissionLevel level = resolvePermission(trip, actorId);
        if (level == null || !level.canEdit()) {
            throw new ForbiddenException("You do not have permission to edit this trip");
        }
    }

    @Transactional
    public Trip shareTrip(UUID tripId, UUID actorId, ShareTripRequestDTO request) {
        Trip trip = tripRepository.findByIdWithLock(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        requireCanManageMembers(trip, actorId);

        if (request == null || request.getUsers() == null || request.getUsers().isEmpty()) {
            throw new BadRequestException("At least one user is required");
        }

        for (ShareTripUserItemDTO item : request.getUsers()) {
            inviteOne(trip, actorId, item);
        }

        trip.setUpdatedAt(Instant.now());
        Trip updated = tripRepository.updateTrip(trip);
        tripChatService.ensureConversationIfEligible(tripId);
        return updated;
    }

    private void inviteOne(Trip trip, UUID actorId, ShareTripUserItemDTO item) {
        User invitee = resolveInvitee(item);
        if (invitee.id.equals(actorId)) {
            throw new BadRequestException("You cannot invite yourself");
        }
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(invitee.id)) {
            throw new BadRequestException("Trip creator is already the owner");
        }

        String permission = normalizeInvitePermission(item.getPermission());
        Optional<TripUser> existing = tripRepository.findTripUser(trip.id, invitee.id);
        if (existing.isPresent()) {
            TripUser tu = existing.get();
            tu.setPermissionLevel(permission);
            log.info("Updated collaborator tripId={} userId={} permission={}", trip.id, invitee.id, permission);
            return;
        }

        tripRepository.addTripMember(trip, invitee, permission);
        log.info("Invited collaborator tripId={} userId={} permission={}", trip.id, invitee.id, permission);
    }

    private User resolveInvitee(ShareTripUserItemDTO item) {
        if (item.getUserId() != null) {
            User user = userRepository.findById(item.getUserId());
            if (user == null) {
                throw new NotFoundException("User not found with id: " + item.getUserId());
            }
            return user;
        }
        if (item.getEmail() != null && !item.getEmail().isBlank()) {
            String email = item.getEmail().trim().toLowerCase();
            return userRepository
                    .findByEmail(email)
                    .orElseThrow(() -> new NotFoundException(
                            "No registered user with this email. They must sign up first."));
        }
        throw new BadRequestException("userId or email is required");
    }

    private static String normalizeInvitePermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return UserPermissionLevel.ADMIN.name();
        }
        UserPermissionLevel level = UserPermissionLevel.fromString(permission.trim());
        if (level == UserPermissionLevel.OWNER) {
            throw new BadRequestException("Cannot assign OWNER to invited users");
        }
        return level.name();
    }

    @Transactional
    public Trip removeMember(UUID tripId, UUID actorId, UUID memberUserId) {
        Trip trip = tripRepository.findByIdWithLock(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        requireCanManageMembers(trip, actorId);

        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(memberUserId)) {
            throw new BadRequestException("Cannot remove the trip owner");
        }

        boolean removed = tripRepository.removeTripMember(trip, memberUserId);
        if (!removed) {
            throw new NotFoundException("User is not a collaborator on this trip");
        }

        trip.setUpdatedAt(Instant.now());
        Trip updated = tripRepository.updateTrip(trip);
        tripChatService.onMemberRemoved(tripId, memberUserId);
        return updated;
    }

    @Transactional
    public Trip updateMemberPermission(
            UUID tripId, UUID actorId, UUID memberUserId, UpdateSharePermissionDTO body) {
        Trip trip = tripRepository.findByIdWithLock(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        requireCanManageMembers(trip, actorId);

        if (body == null || body.getPermission() == null || body.getPermission().isBlank()) {
            throw new BadRequestException("permission is required");
        }

        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(memberUserId)) {
            throw new BadRequestException("Cannot change owner permission");
        }

        UserPermissionLevel level = UserPermissionLevel.fromString(body.getPermission().trim());
        if (level == UserPermissionLevel.OWNER) {
            throw new BadRequestException("Cannot assign OWNER via share API");
        }

        TripUser tripUser =
                tripRepository
                        .findTripUser(trip.id, memberUserId)
                        .orElseThrow(() -> new NotFoundException("User is not a collaborator on this trip"));

        tripUser.setPermissionLevel(level.name());
        trip.setUpdatedAt(Instant.now());
        return tripRepository.updateTrip(trip);
    }

    public List<TripUserDTO> buildCollaboratorList(Trip trip) {
        List<TripUserDTO> result = new ArrayList<>();
        User creator = trip.getCreatedBy();
        if (creator != null) {
            boolean creatorInList =
                    trip.getUsers() != null
                            && trip.getUsers().stream()
                                    .anyMatch(tu -> tu.getUser() != null && tu.getUser().id.equals(creator.id));
            if (!creatorInList) {
                result.add(TripMapper.toTripUserDto(creator, UserPermissionLevel.OWNER));
            }
        }
        if (trip.getUsers() != null) {
            for (TripUser tu : trip.getUsers()) {
                if (tu.getUser() != null) {
                    UserPermissionLevel level =
                            UserPermissionLevel.fromString(
                                    tu.getPermissionLevel() != null
                                            ? tu.getPermissionLevel()
                                            : UserPermissionLevel.VIEWER.name());
                    result.add(TripMapper.toTripUserDto(tu.getUser(), level));
                }
            }
        }
        return result;
    }
}
