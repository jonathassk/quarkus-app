package org.example.application.usecases.interfaces;

import org.example.domain.entity.Trip;

public interface CreateTripUseCase {
    Trip createTrip(Trip trip, Long createdBy);
}
