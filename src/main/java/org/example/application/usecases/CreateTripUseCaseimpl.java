package org.example.application.usecases;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.services.TripService;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.domain.entity.*;
import org.example.domain.repository.*;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CreateTripUseCaseimpl implements CreateTripUseCase {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripService tripService;
    private final ActivityRepository activityRepository;
    private final MealRepository mealRepository;

    @Override
    public Trip createTrip(TripRequestDTO tripRequest) {
        ModelMapper mapper = ModelMapperFactory.createModelMapper();
        Trip trip = mapper.map(tripRequest, Trip.class);

        User creator = userRepository.findById(tripRequest.getCreatedBy());
        if (creator == null) throw new NotFoundException("User not found");
        trip.setCreatedBy(creator);

        TripUser tripUser = tripService.createTripUser(creator, trip);

        if (tripRequest.getSegments() != null && !tripRequest.getSegments().isEmpty()) {
            List<TripSegment> segments = new ArrayList<>();

            for (TripSegmentDTO segmentDTO : tripRequest.getSegments()) {
                TripSegment segment = mapper.map(segmentDTO, TripSegment.class);
                segment.setTrip(trip);
                
                // Mapear e persistir meals
                if (segmentDTO.getMeals() != null) {
                    List<Meal> meals = new ArrayList<>();
                    for (MealDTO mealDTO : segmentDTO.getMeals()) {
                        Meal meal = mapper.map(mealDTO, Meal.class);
                        meal.setSegment(segment);
                        meals.add(meal);
                    }
                    segment.setMeals(meals);
                }

                // Mapear e persistir activities
                if (segmentDTO.getActivities() != null) {
                    List<Activity> activities = new ArrayList<>();
                    for (ActivityDTO activityDTO : segmentDTO.getActivities()) {
                        Activity activity = mapper.map(activityDTO, Activity.class);
                        activity.setSegment(segment);
                        activities.add(activity);
                    }
                    segment.setActivities(activities);
                }

                segments.add(segment);
            }

            trip.setSegments(segments);
        }

        List<TripUser> tripUsers = new ArrayList<>();
        tripUsers.add(tripUser);
        trip.setUsers(tripUsers);

        tripRepository.persist(trip);
        return trip;
    }
}
