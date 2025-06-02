package org.example.application.usecases;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.repository.TripRepository;

@RequiredArgsConstructor
public class UpdateTripUseCaseImpl implements UpdateTripUseCase {

    private final TripRepository tripRepository;

    @Override
    public Trip updateTrip(Long tripId, TripRequestDTO tripRequestDTO) {
        return null;
    }

    @Override
    public Trip updateUsersTrip(Long tripId, TripRequestDTO tripRequestDTO) {
        return null;
    }

    @Override
    public Trip updateTripUserRelation(Long tripId, Long userId, String permissionLevel) {
        return null;
    }

    @Override
    public Trip updateNameAndDescription(Long tripId, NameDescriptionTravelRequestDto tripRequestDTO) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) throw new NotFoundException();

        trip.setName(tripRequestDTO.getName());
        trip.setDescription(tripRequestDTO.getDescription());
        tripRepository.persist(trip);

        return trip;
    }
}
