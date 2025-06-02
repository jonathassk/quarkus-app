package org.example.application.services.impl;

import org.example.application.services.TripService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripUser;
import org.example.domain.entity.User;

public class TripServiceImpl implements TripService {
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
        return null;
    }

    @Override
    public TripUser updateTripUserRelation(TripUser tripUser) {
        return null;
    }

    @Override
    public String deleteTripUserRelation(TripUser tripUser) {
        return "";
    }
}
