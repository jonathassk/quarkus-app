package org.example.application.usecases;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.UserInlcudeRequestDTO;
import org.example.application.dto.trip.TripUserDTO;
import org.example.application.services.TripService;
import org.example.application.services.chat.TripChatService;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.modelmapper.ModelMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class UpdateTripUseCaseImpl implements UpdateTripUseCase {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripService tripService;
    private final TripChatService tripChatService;
    private final ModelMapper modelMapper = new ModelMapper();

    @Transactional
    @Override
    public Trip updateTrip(UUID tripId, TripRequestDTO tripRequestDTO) {
        try {
            Trip trip = tripRepository.findByIdWithLock(tripId);
            if (trip == null) {
                log.warn("Update trip failed: trip not found, tripId={}", tripId);
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            if (trip.getCreatedBy() == null) {
                log.error("Update trip failed: creator missing on trip, tripId={}", trip.id);
                throw new NotFoundException("Trip creator not found");
            }

            tripService.updateBasicTripInfo(trip, tripRequestDTO);

            if (tripRequestDTO.getSegments() != null) {
                tripService.updateTripSegments(trip, tripRequestDTO.getSegments());
            }

            if (tripRequestDTO.getUsers() != null) {
                List<UserInlcudeRequestDTO> usersRequest = new ArrayList<>();
                for (TripUserDTO userDTO : tripRequestDTO.getUsers()) {
                    if (userDTO == null || userDTO.getUserId() == null || userDTO.getPermissionLevel() == null) {
                        continue;
                    }
                    usersRequest.add(UserInlcudeRequestDTO.builder()
                            .userId(userDTO.getUserId())
                            .permissionLevel(userDTO.getPermissionLevel().name())
                            .build());
                }
                if (!usersRequest.isEmpty()) {
                    List<UUID> userIds = usersRequest.stream().map(UserInlcudeRequestDTO::getUserId).toList();
                    List<User> users = userRepository.list("id in ?1", userIds);
                    if (users.size() != userIds.size()) {
                        log.warn("Update trip failed: some users not found, tripId={}, requested={}, found={}",
                                tripId, userIds.size(), users.size());
                        throw new NotFoundException("Some users were not found");
                    }
                    tripRepository.updateTripUsers(
                            trip,
                            users,
                            usersRequest.stream().collect(Collectors.toMap(UserInlcudeRequestDTO::getUserId, UserInlcudeRequestDTO::getPermissionLevel))
                    );
                }
            }

            trip.setUpdatedAt(Instant.now());
            return tripRepository.updateTrip(trip);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Update trip failed: tripId={}", tripId, e);
            throw new RuntimeException("Error updating trip: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Trip updateUsersTrip(UUID tripId, List<UserInlcudeRequestDTO> usersRequest) {
        try {
            Trip trip = tripRepository.findByIdWithLock(tripId);
            if (trip == null) {
                log.warn("Update trip users failed: trip not found, tripId={}", tripId);
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            List<UUID> userIds = usersRequest.stream()
                    .map(UserInlcudeRequestDTO::getUserId)
                    .toList();

            List<User> users = userRepository.list("id in ?1", userIds);
            if (users.size() != userIds.size()) {
                log.warn("Update trip users failed: some users not found, tripId={}, requested={}, found={}",
                        tripId, userIds.size(), users.size());
                throw new NotFoundException("Some users were not found");
            }

            Map<UUID, String> userPermissions = usersRequest.stream()
                    .collect(Collectors.toMap(
                            UserInlcudeRequestDTO::getUserId,
                            UserInlcudeRequestDTO::getPermissionLevel
                    ));

            trip.setUpdatedAt(Instant.now());
            return tripRepository.updateTripUsers(trip, users, userPermissions);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Update trip users failed: tripId={}", tripId, e);
            throw new RuntimeException("Error updating trip users: " + e.getMessage(), e);
        }
    }

    @Override
    public Trip updateTripUserRelation(UUID tripId, UUID userId, String permissionLevel) {
        return null;
    }

    @Override
    @Transactional
    public Trip updateNameAndDescription(UUID tripId, NameDescriptionTravelRequestDto tripRequestDTO) {
        try {
            Trip trip = tripRepository.findByIdWithLock(tripId);
            if (trip == null) {
                log.warn("Update trip name/description failed: trip not found, tripId={}", tripId);
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            if (tripRequestDTO.getName() != null) trip.setName(tripRequestDTO.getName());
            if (tripRequestDTO.getDescription() != null) trip.setDescription(tripRequestDTO.getDescription());
            trip.setUpdatedAt(Instant.now());

            return tripRepository.updateTrip(trip);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Update trip name/description failed: tripId={}", tripId, e);
            throw new RuntimeException("Error updating trip name and description: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteTrip(UUID tripId, UUID requesterUserId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            log.warn("Delete trip failed: trip not found, tripId={}", tripId);
            throw new NotFoundException("Trip not found with id: " + tripId);
        }
        User creator = trip.getCreatedBy();
        if (creator == null || !creator.id.equals(requesterUserId)) {
            log.warn("Delete trip rejected: userId={} is not creator of tripId={}", requesterUserId, tripId);
            throw new ForbiddenException("Only the trip creator can delete this trip");
        }
        tripChatService.archiveConversation(tripId);
        tripRepository.delete(trip);
    }
}
