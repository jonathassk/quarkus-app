package org.example.application.usecases;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.UserInlcudeRequestDTO;
import org.example.application.services.TripService;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.modelmapper.ModelMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class UpdateTripUseCaseImpl implements UpdateTripUseCase {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripService tripService;
    private final ModelMapper modelMapper = new ModelMapper();

    @Transactional
    @Override
    public Trip updateTrip(Long tripId, TripRequestDTO tripRequestDTO) {
        log.info("Starting update for trip with id: {}", tripId);
        try {
            Trip trip = tripRepository.findByIdWithLock(tripId);
            if (trip == null) {
                log.error("Trip not found with id: {}", tripId);
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            if (trip.getCreatedBy() == null) {
                log.error("Trip creator not found for trip id: {}", trip.id);
                throw new NotFoundException("Trip creator not found");
            }

            log.info("Trip found with id: {}", tripId);
            tripService.updateBasicTripInfo(trip, tripRequestDTO);

            if (tripRequestDTO.getSegments() != null) {
                tripService.updateTripSegments(trip, tripRequestDTO.getSegments());
            }

            trip.setUpdatedAt(Instant.now());
            return tripRepository.updateTrip(trip);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating trip: {}", e.getMessage(), e);
            throw new RuntimeException("Error updating trip: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Trip updateUsersTrip(Long tripId, List<UserInlcudeRequestDTO> usersRequest) {
        log.info("Starting update users for trip with id: {}", tripId);
        try {
            Trip trip = tripRepository.findByIdWithLock(tripId);
            if (trip == null) {
                log.error("Trip not found with id: {}", tripId);
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            List<Long> userIds = usersRequest.stream()
                .map(UserInlcudeRequestDTO::getUserId)
                .toList();
            
            List<User> users = userRepository.list("id in ?1", userIds);
            if (users.size() != userIds.size()) {
                log.error("Some users were not found. Requested: {}, Found: {}", userIds.size(), users.size());
                throw new NotFoundException("Some users were not found");
            }

            // Mapeia as permissões dos usuários
            Map<Long, String> userPermissions = usersRequest.stream()
                .collect(Collectors.toMap(
                    UserInlcudeRequestDTO::getUserId,
                    UserInlcudeRequestDTO::getPermissionLevel
                ));

            trip.setUpdatedAt(Instant.now());
            return tripRepository.updateTripUsers(trip, users, userPermissions);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating trip users: {}", e.getMessage(), e);
            throw new RuntimeException("Error updating trip users: " + e.getMessage(), e);
        }
    }

    @Override
    public Trip updateTripUserRelation(Long tripId, Long userId, String permissionLevel) {
        return null;
    }

    @Override
    @Transactional
    public Trip updateNameAndDescription(Long tripId, NameDescriptionTravelRequestDto tripRequestDTO) {
        try {
            log.info("Starting name and description update for trip id: {}", tripId);
            Trip trip = tripRepository.findByIdWithLock(tripId);
            if (trip == null) {
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            if (tripRequestDTO.getName() != null) trip.setName(tripRequestDTO.getName());
            if (tripRequestDTO.getDescription() != null) trip.setDescription(tripRequestDTO.getDescription());
            trip.setUpdatedAt(Instant.now());
            
            log.info("Merging trip changes");
            return tripRepository.updateTrip(trip);
        } catch (Exception e) {
            log.error("Error updating trip name and description: {}", e.getMessage(), e);
            throw new RuntimeException("Error updating trip name and description: " + e.getMessage(), e);
        }
    }
}
