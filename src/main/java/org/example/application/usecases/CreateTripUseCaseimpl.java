package org.example.application.usecases;

import lombok.RequiredArgsConstructor;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;

@RequiredArgsConstructor
public class CreateTripUseCaseimpl implements CreateTripUseCase {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    @Override
    public Trip createTrip(Trip trip, Long createdBy) {
        trip.setCreatedBy(userRepository.findById(createdBy));
        tripRepository.persist(trip);
        return trip;
    }
}
