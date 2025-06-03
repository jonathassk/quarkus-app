package org.example.application.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.services.TripService;
import org.example.domain.entity.*;
import org.example.domain.repository.TripSegmentRepository;
import org.modelmapper.ModelMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripSegmentRepository tripSegmentRepository;
    private final ModelMapper modelMapper = new ModelMapper();

    @Override
    public TripUser createTripUser(User user, Trip trip) {
        TripUser tripUser = new TripUser();
        tripUser.setUser(user);
        tripUser.setTrip(trip);
        tripUser.setPermissionLevel("OWNER");
        return tripUser;
    }

    @Override
    public TripUser addUserToTrip(User user, Trip trip, String permissionLevel) {
        TripUser tripUser = new TripUser();
        tripUser.setUser(user);
        tripUser.setTrip(trip);
        tripUser.setPermissionLevel(permissionLevel);
        return tripUser;
    }

    @Override
    public TripUser updateTripUserRelation(TripUser tripUser) {
        return null;
    }

    @Override
    public String deleteTripUserRelation(TripUser tripUser) {
        return "";
    }

    @Override
    public List<TripSegment> updateTripSegment(List<TripSegmentDTO> tripSegment) {
        return tripSegment.stream()
                .map(segmentDTO -> modelMapper.map(segmentDTO, TripSegment.class))
                .toList();
    }

    @Override
    public List<Activity> updateActivities(List<ActivityDTO> activity) {
        return activity.stream().map(activityDTO ->
            modelMapper.map(activityDTO, Activity.class)
        ).toList();
    }

    @Override
    public List<Meal> updateMeal(List<MealDTO> meal) {
        return meal.stream().map(
            mealDTO -> modelMapper.map(mealDTO, Meal.class)
        ).toList();
    }

    @Override
    public void updateBasicTripInfo(Trip trip, TripRequestDTO dto) {
        if (dto.getCoverImageUrl() != null) trip.setCoverImageUrl(dto.getCoverImageUrl());
        if (dto.getName() != null) trip.setName(dto.getName());
        if (dto.getDescription() != null) trip.setDescription(dto.getDescription());
        if (dto.getStartDate() != null) trip.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) trip.setEndDate(dto.getEndDate());
        if (dto.getBudgetTotal() != null) trip.setBudgetTotal(dto.getBudgetTotal());

        trip.setUpdatedAt(Instant.now());
    }

    @Override
    public void updateTripSegments(Trip trip, List<TripSegmentDTO> segmentDTOs) {
        log.info("Updating segments for trip id: {}", trip.id);

        // Remove segmentos existentes
        if (trip.getSegments() != null) {
            log.info("Removing {} existing segments", trip.getSegments().size());
            trip.getSegments().forEach(tripSegmentRepository::delete);
            trip.getSegments().clear();
        } else {
            trip.setSegments(new ArrayList<>());
        }

        // Cria novos segmentos
        segmentDTOs.forEach(segmentDTO -> {
            TripSegment newSegment = createNewSegment(segmentDTO, trip);
            tripSegmentRepository.persist(newSegment);
            trip.getSegments().add(newSegment);
            log.info("Successfully added new segment with cityId: {}", segmentDTO.getCityId());
        });
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
