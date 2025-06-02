package org.example.application.usecases.interfaces;

import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.domain.entity.Trip;

public interface CreateTripUseCase {
    Trip createTrip(TripRequestDTO tripRequest);
}
