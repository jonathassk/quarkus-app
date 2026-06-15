package org.example.infrastructure.mapper;

import org.example.application.dto.trip.TripUserDTO;
import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.application.services.TripCollaborationService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.TripStatus;
import org.example.domain.enums.UserPermissionLevel;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.convention.MatchingStrategies;

import java.time.LocalDate;

public class TripMapper {

    /** Avoids mapping User → Long on createdBy (causes NumberFormatException on "User&lt;id&gt;"). */
    private static final ModelMapper TRIP_TO_RESPONSE =
            createTripResponseModelMapper();

    private TripMapper() {
    }

    private static ModelMapper createTripResponseModelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper
                .getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STANDARD)
                .setSkipNullEnabled(true);
        modelMapper.addMappings(
                new PropertyMap<Trip, TripResponseDTO>() {
                    @Override
                    protected void configure() {
                        skip(destination.getCreatedBy());
                        skip(destination.getUsers());
                        skip(destination.getStatus());
                    }
                });
        return modelMapper;
    }

    public static TripResponseDTO mapToTripResponseDTO(Trip trip) {
        return mapToTripResponseDTO(trip, null);
    }

    public static TripResponseDTO mapToTripResponseDTO(Trip trip, TripCollaborationService collaborationService) {
        TripResponseDTO dto = TRIP_TO_RESPONSE.map(trip, TripResponseDTO.class);
        dto.setStatus(TripStatus.fromDates(trip.getStartDate(), trip.getEndDate(), LocalDate.now()));
        if (trip.getCreatedBy() != null) {
            dto.setCreatedBy(trip.getCreatedBy().id);
        }
        if (trip.getWorkspace() != null) {
            dto.setWorkspaceId(trip.getWorkspace().id);
        }
        if (collaborationService != null) {
            dto.setUsers(collaborationService.buildCollaboratorList(trip));
        }
        return dto;
    }

    public static TripUserDTO toTripUserDto(User user, UserPermissionLevel level) {
        return TripUserDTO.builder()
                .userId(user.id)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .permissionLevel(level)
                .build();
    }
}
