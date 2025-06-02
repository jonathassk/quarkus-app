package org.example.application.services;

import org.example.domain.entity.Trip;
import org.example.domain.entity.TripUser;
import org.example.domain.entity.User;

public interface TripService {
    TripUser createTripUser(User user, Trip trip);
    TripUser addUserToTrip(User user, Trip trip, String permissionLevel);
    TripUser updateTripUserRelation(TripUser tripUser);
    String deleteTripUserRelation(TripUser tripUser);
}
