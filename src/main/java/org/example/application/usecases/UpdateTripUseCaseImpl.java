package org.example.application.usecases;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.services.TripService;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.*;
import org.example.domain.repository.TripRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

            validateTripCreator(trip);
            updateBasicTripInfo(trip, tripRequestDTO);

            if (tripRequestDTO.getSegments() != null) {
                updateTripSegments(trip, tripRequestDTO.getSegments(), em);
            }

            em.merge(trip);
            em.flush();
            log.info("Successfully updated trip with id: {}", tripId);
            return trip;
        } catch (NotFoundException e) {
            throw e; // Re-lança exceções específicas
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
                log.error("Trip not found with id: {}", tripId);
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

    private void validateTripCreator(Trip trip) {
        if (trip.getCreatedBy() == null) {
            log.error("Trip creator not found for trip id: {}", trip.id);
            throw new NotFoundException("Trip creator not found");
        }
    }

    private void updateBasicTripInfo(Trip trip, TripRequestDTO dto) {
        log.info("Updating basic trip information for trip id: {}", trip.id);

        if (dto.getCoverImageUrl() != null) trip.setCoverImageUrl(dto.getCoverImageUrl());
        if (dto.getName() != null) trip.setName(dto.getName());
        if (dto.getDescription() != null) trip.setDescription(dto.getDescription());
        if (dto.getStartDate() != null) trip.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) trip.setEndDate(dto.getEndDate());
        if (dto.getBudgetTotal() != null) trip.setBudgetTotal(dto.getBudgetTotal());

        trip.setUpdatedAt(Instant.now());
    }

    private void updateTripSegments(Trip trip, List<TripSegmentDTO> segmentDTOs, EntityManager em) {
        log.info("Updating segments for trip id: {}", trip.id);

        // Remove segmentos existentes
        removeExistingSegments(trip, em);

        // Adiciona novos segmentos
        segmentDTOs.forEach(segmentDTO -> {
            TripSegment newSegment = createNewSegment(segmentDTO, trip);
            em.persist(newSegment);
            trip.getSegments().add(newSegment);
            log.info("Successfully added new segment with cityId: {}", segmentDTO.getCityId());
        });
    }

    private void removeExistingSegments(Trip trip, EntityManager em) {
        if (trip.getSegments() != null) {
            log.info("Removing {} existing segments", trip.getSegments().size());
            new ArrayList<>(trip.getSegments()).forEach(em::remove);
            trip.getSegments().clear();
        } else {
            trip.setSegments(new ArrayList<>());
        }
    }

    private TripSegment createNewSegment(TripSegmentDTO segmentDTO, Trip trip) {
        TripSegment segment = new TripSegment();
        segment.setCityId(segmentDTO.getCityId());
        segment.setArrivalDate(segmentDTO.getArrivalDate());
        segment.setDepartureDate(segmentDTO.getDepartureDate());
        segment.setNotes(segmentDTO.getNotes());
        segment.setTrip(trip);

        if (segmentDTO.getMeals() != null && !segmentDTO.getMeals().isEmpty()) {
            log.info("Adding {} meals to segment", segmentDTO.getMeals().size());
            segment.setMeals(createMeals(segmentDTO.getMeals(), segment));
        }

        if (segmentDTO.getActivities() != null && !segmentDTO.getActivities().isEmpty()) {
            log.info("Adding {} activities to segment", segmentDTO.getActivities().size());
            segment.setActivities(createActivities(segmentDTO.getActivities(), segment));
        }

        return segment;
    }

    private List<Meal> createMeals(List<MealDTO> mealDTOs, TripSegment segment) {
        return mealDTOs.stream()
                .map(mealDTO -> {
                    Meal meal = modelMapper.map(mealDTO, Meal.class);
                    meal.setSegment(segment);
                    return meal;
                })
                .collect(Collectors.toList());
    }

    private List<Activity> createActivities(List<ActivityDTO> activityDTOs, TripSegment segment) {
        return activityDTOs.stream()
                .map(activityDTO -> {
                    Activity activity = modelMapper.map(activityDTO, Activity.class);
                    activity.setSegment(segment);
                    return activity;
                })
                .collect(Collectors.toList());
    }
}
