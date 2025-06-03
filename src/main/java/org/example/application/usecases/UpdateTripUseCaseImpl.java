package org.example.application.usecases;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.services.TripService;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.repository.TripRepository;

import java.time.Instant;

@RequiredArgsConstructor
public class UpdateTripUseCaseImpl implements UpdateTripUseCase {

    private final TripRepository tripRepository;
    private final TripService tripService;

    @Override
    public Trip updateTrip(Long tripId, TripRequestDTO tripRequestDTO) {
        Trip trip = tripRepository.findById(tripId);
        User user = trip.getCreatedBy();

        trip.setCoverImageUrl(tripRequestDTO.getCoverImageUrl());
        trip.setName(tripRequestDTO.getName());
        trip.setDescription(tripRequestDTO.getDescription());
        trip.setStartDate(tripRequestDTO.getStartDate());
        trip.setEndDate(tripRequestDTO.getEndDate());
        trip.setBudgetTotal(tripRequestDTO.getBudgetTotal());
        trip.setSegments(tripService.updateTripSegment(tripRequestDTO.getSegments()));
        trip.setUpdatedAt(Instant.now());

        tripRepository.persist(trip);
        return trip;
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
