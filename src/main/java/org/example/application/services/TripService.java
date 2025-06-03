package org.example.application.services;

import org.example.application.dto.trip.ActivityDTO;
import org.example.application.dto.trip.MealDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.domain.entity.*;

import java.util.List;

public interface TripService {
    TripUser createTripUser(User user, Trip trip);
    TripUser addUserToTrip(User user, Trip trip, String permissionLevel);
    TripUser updateTripUserRelation(TripUser tripUser);
    String deleteTripUserRelation(TripUser tripUser);
    List<TripSegment> updateTripSegment(List<TripSegmentDTO> tripSegment);
    List<Activity> updateActivities(List<ActivityDTO> activity);
    List<Meal> updateMeal(List<MealDTO> meal);
}
