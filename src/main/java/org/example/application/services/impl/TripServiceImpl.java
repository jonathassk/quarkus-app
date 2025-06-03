package org.example.application.services.impl;

import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.services.TripService;
import org.example.domain.entity.*;
import org.modelmapper.ModelMapper;

import java.util.List;

public class TripServiceImpl implements TripService {

    ModelMapper modelMapper = new ModelMapper();
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

}
