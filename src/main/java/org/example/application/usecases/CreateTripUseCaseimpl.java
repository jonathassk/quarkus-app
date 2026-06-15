package org.example.application.usecases;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.dto.trip.TripUserDTO;
import org.example.application.services.TripService;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.domain.entity.*;
import org.example.domain.repository.*;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
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
        if (creator == null) {
            log.warn("Create trip failed: creator not found, createdBy={}", tripRequest.getCreatedBy());
            throw new NotFoundException("User not found");
        }
        trip.setCreatedBy(creator);

        Long workspaceId = tripRequest.getWorkspaceId();
        Workspace workspace = null;
        if (workspaceId != null) {
            workspace = Workspace.findById(workspaceId);
            if (workspace == null) {
                log.warn("Create trip failed: workspace not found, workspaceId={}", workspaceId);
                throw new NotFoundException("Workspace not found");
            }
        } else {
            WorkspaceMember member = WorkspaceMember.find("user", creator).firstResult();
            if (member != null) {
                workspace = member.getWorkspace();
            } else {
                workspace = Workspace.builder()
                        .name("Workspace Pessoal de " + (creator.getFullName() != null && !creator.getFullName().isBlank() ? creator.getFullName() : creator.getUsername()))
                        .planType("FREE")
                        .primaryColor("#000000")
                        .build();
                workspace.persist();

                WorkspaceMember newMember = WorkspaceMember.builder()
                        .workspace(workspace)
                        .user(creator)
                        .role(org.example.domain.enums.WorkspaceRole.OWNER)
                        .build();
                newMember.persist();
                log.info("Created JIT personal workspace id={} for user id={}", workspace.id, creator.id);
            }
        }
        trip.setWorkspace(workspace);
        trip.setDurationDays(tripRequest.getDurationDays());
        trip.setTargetMonth(tripRequest.getTargetMonth());

        if (tripRequest.getSegments() != null && !tripRequest.getSegments().isEmpty()) {
            List<TripSegment> segments = new ArrayList<>();

            for (TripSegmentDTO segmentDTO : tripRequest.getSegments()) {
                TripSegment segment = new TripSegment();
                segment.setCityId(segmentDTO.getCityId());
                segment.setArrivalDate(segmentDTO.getArrivalDate());
                segment.setDepartureDate(segmentDTO.getDepartureDate());
                segment.setStartDay(segmentDTO.getStartDay());
                segment.setEndDay(segmentDTO.getEndDay());
                segment.setNotes(segmentDTO.getNotes());
                segment.setDailyCost(segmentDTO.getDailyCost());
                segment.setTrip(trip);

                if (segmentDTO.getMeals() != null) {
                    List<Meal> meals = new ArrayList<>();
                    for (MealDTO mealDTO : segmentDTO.getMeals()) {
                        Meal meal = new Meal();
                        meal.setName(mealDTO.getName());
                        meal.setMealType(mealDTO.getMealType());
                        meal.setDescription(mealDTO.getDescription());
                        meal.setRestaurantName(mealDTO.getRestaurantName());
                        meal.setLocation(mealDTO.getRestaurantName());
                        meal.setAddress(mealDTO.getAddress());
                        meal.setLatitude(mealDTO.getLatitude());
                        meal.setLongitude(mealDTO.getLongitude());
                        meal.setStartTime(mealDTO.getStartTime());
                        meal.setEndTime(mealDTO.getEndTime());
                        meal.setDate(mealDTO.getDate());
                        meal.setDayNumber(mealDTO.getDayNumber());
                        meal.setCost(mealDTO.getCost());
                        meal.setNotes(mealDTO.getNotes());
                        meal.setSegment(segment);
                        meals.add(meal);
                    }
                    segment.setMeals(meals);
                }

                if (segmentDTO.getActivities() != null) {
                    List<Activity> activities = new ArrayList<>();
                    for (ActivityDTO activityDTO : segmentDTO.getActivities()) {
                        Activity activity = new Activity();
                        activity.setName(activityDTO.getName());
                        activity.setActivityType(activityDTO.getActivityType());
                        activity.setAddress(activityDTO.getAddress());
                        activity.setLatitude(activityDTO.getLatitude());
                        activity.setLongitude(activityDTO.getLongitude());
                        activity.setStartTime(activityDTO.getStartTime());
                        activity.setEndTime(activityDTO.getEndTime());
                        activity.setDate(activityDTO.getDate());
                        activity.setDayNumber(activityDTO.getDayNumber());
                        activity.setCost(activityDTO.getCost());
                        activity.setNotes(activityDTO.getNotes());
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
        if (tripRequest.getUsers() != null && !tripRequest.getUsers().isEmpty()) {
            for (TripUserDTO userDTO : tripRequest.getUsers()) {
                if (userDTO == null || userDTO.getUserId() == null || userDTO.getPermissionLevel() == null) {
                    continue;
                }
                User user = userRepository.findById(userDTO.getUserId());
                if (user == null) {
                    log.warn("Create trip failed: trip user not found, userId={}", userDTO.getUserId());
                    throw new NotFoundException("User not found with id: " + userDTO.getUserId());
                }
                TripUser tripUser = tripService.addUserToTrip(user, trip, userDTO.getPermissionLevel().name());
                tripUsers.add(tripUser);
            }
        }

        boolean creatorIncluded = tripUsers.stream()
                .map(TripUser::getUser)
                .filter(Objects::nonNull)
                .anyMatch(user -> creator.id.equals(user.id));
        if (!creatorIncluded) {
            tripUsers.add(tripService.createTripUser(creator, trip));
        }

        trip.setUsers(tripUsers);

        tripRepository.persist(trip);
        return trip;
    }
}
