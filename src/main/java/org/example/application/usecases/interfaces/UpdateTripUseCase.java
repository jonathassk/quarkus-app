package org.example.application.usecases.interfaces;

import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.UserInlcudeRequestDTO;
import org.example.domain.entity.Trip;

import java.util.List;

public interface UpdateTripUseCase {
    Trip updateTrip(Long tripId, TripRequestDTO tripRequestDTO);
    Trip updateUsersTrip(Long tripId, List<UserInlcudeRequestDTO> tripRequestDTO);
    Trip updateTripUserRelation(Long tripId, Long userId, String permissionLevel);
    Trip updateNameAndDescription(Long tripId, NameDescriptionTravelRequestDto tripRequestDTO);
}
