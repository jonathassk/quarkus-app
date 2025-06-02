package org.example.application.usecases.interfaces;

import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.domain.entity.Trip;

public interface UpdateTripUseCase {
    Trip updateTrip(Long tripId, TripRequestDTO tripRequestDTO);
    Trip updateUsersTrip(Long tripId, TripRequestDTO tripRequestDTO);
    Trip updateTripUserRelation(Long tripId, Long userId, String permissionLevel);
    Trip updateNameAndDescription(Long tripId, NameDescriptionTravelRequestDto tripRequestDTO);
}
