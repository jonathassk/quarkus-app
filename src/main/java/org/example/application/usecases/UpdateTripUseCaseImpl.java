package org.example.application.usecases;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.services.TripService;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.repository.TripRepository;
import org.modelmapper.ModelMapper;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class UpdateTripUseCaseImpl implements UpdateTripUseCase {

    private final TripRepository tripRepository;
    private final TripService tripService;
    private final ModelMapper modelMapper = new ModelMapper();

    public Trip updateTrip(Long tripId, TripRequestDTO tripRequestDTO) {
        log.info("Starting update for trip with id: {}", tripId);
        EntityManager em = tripRepository.getEntityManager();

        try {
            Trip trip = em.find(Trip.class, tripId);
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
                tripService.updateTripSegments(trip, tripRequestDTO.getSegments(), em);
            }

            em.merge(trip);
            em.flush();
            log.info("Successfully updated trip with id: {}", tripId);
            return trip;
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating trip: {}", e.getMessage(), e);
            throw new RuntimeException("Error updating trip: " + e.getMessage(), e);
        }
    }

    @Override
    public Trip updateUsersTrip(Long tripId, TripRequestDTO tripRequestDTO) {
        return null;
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
            EntityManager em = tripRepository.getEntityManager();
            
            Trip trip = em.find(Trip.class, tripId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (trip == null) {
                throw new NotFoundException("Trip not found with id: " + tripId);
            }

            if (tripRequestDTO.getName() != null) trip.setName(tripRequestDTO.getName());
            if (tripRequestDTO.getDescription() != null) trip.setDescription(tripRequestDTO.getDescription());
            trip.setUpdatedAt(Instant.now());
            
            log.info("Merging trip changes");
            em.merge(trip);
            em.flush();
            log.info("Successfully updated name and description for trip id: {}", tripId);
            
            return trip;
        } catch (Exception e) {
            log.error("Error updating trip name and description: {}", e.getMessage(), e);
            throw new RuntimeException("Error updating trip name and description: " + e.getMessage(), e);
        }
    }
}
