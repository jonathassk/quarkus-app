package org.example.infrastructure.mapper;

import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.domain.entity.Trip;
import org.example.domain.enums.TripStatus;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import java.time.LocalDate;

public class TripMapper {

    public static TripResponseDTO mapToTripResponseDTO(Trip trip) {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STANDARD)
                .setSkipNullEnabled(true);
        TripResponseDTO dto = modelMapper.map(trip, TripResponseDTO.class);
        dto.setStatus(TripStatus.fromDates(trip.getStartDate(), trip.getEndDate(), LocalDate.now()));
        return dto;
    }
}
