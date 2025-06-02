package org.example.infrastructure.mapper;

import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.domain.entity.Trip;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

public class TripMapper {

    public static TripResponseDTO mapToTripResponseDTO(Trip trip) {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STANDARD)
                .setSkipNullEnabled(true);
        return modelMapper.map(trip, TripResponseDTO.class);
    }
}
