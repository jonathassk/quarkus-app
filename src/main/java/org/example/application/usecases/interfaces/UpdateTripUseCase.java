package org.example.application.usecases.interfaces;

import java.util.UUID;

import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.UserInlcudeRequestDTO;
import org.example.domain.entity.Trip;

import java.util.List;

public interface UpdateTripUseCase {
    Trip updateTrip(UUID tripId, TripRequestDTO tripRequestDTO);
    Trip updateUsersTrip(UUID tripId, List<UserInlcudeRequestDTO> tripRequestDTO);
    Trip updateTripUserRelation(UUID tripId, UUID userId, String permissionLevel);
    Trip updateNameAndDescription(UUID tripId, NameDescriptionTravelRequestDto tripRequestDTO);

    /** Apaga a viagem; somente o usuário criador pode executar. */
    void deleteTrip(UUID tripId, UUID requesterUserId);
}
