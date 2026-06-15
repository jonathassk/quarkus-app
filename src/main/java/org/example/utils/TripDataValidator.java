package org.example.utils;

import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.TripSegmentDTO;

public class TripDataValidator {
    public static void validateTripRequest(TripRequestDTO tripRequest) {
        if (tripRequest.getName() == null || tripRequest.getName().isEmpty()) throw new IllegalArgumentException("Trip name cannot be null or empty.");
        if (tripRequest.getStartDate() != null && tripRequest.getEndDate() != null) {
            if (tripRequest.getStartDate().isAfter(tripRequest.getEndDate())) {
                throw new IllegalArgumentException("Trip start date cannot be after end date.");
            }
        }
        if (tripRequest.getSegments() == null || tripRequest.getSegments().isEmpty()
                || tripRequest.getSegments().stream().anyMatch(segment -> segment.getActivities() == null || segment.getActivities().isEmpty())) {
            throw new IllegalArgumentException("Trip must have at least one segment with activities.");
        }
        for (TripSegmentDTO segment : tripRequest.getSegments()) {
            if (segment.getActivities() == null || segment.getActivities().isEmpty()) {
                throw new IllegalArgumentException("Each trip segment must have at least one activity.");
            }
            for (var activity : segment.getActivities()) {
                if (activity.getName() == null || activity.getName().isEmpty()) throw new IllegalArgumentException("Activity name cannot be null or empty.");
            }
        }
        if (tripRequest.getUsers() == null || tripRequest.getUsers().isEmpty()) throw new IllegalArgumentException("Trip must have at least one user.");
    }
}
