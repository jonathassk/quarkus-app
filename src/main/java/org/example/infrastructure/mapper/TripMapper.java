package org.example.infrastructure.mapper;

import org.example.application.dto.trip.TripUserDTO;
import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.application.services.TripCollaborationService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.enums.TripStatus;
import org.example.domain.enums.TripUnlockKind;
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
        return mapToTripResponseDTO(trip, collaborationService, java.util.Set.of());
    }

    public static TripResponseDTO mapToTripResponseDTO(
            Trip trip,
            TripCollaborationService collaborationService,
            java.util.Set<TripUnlockKind> unlockKinds) {
        TripResponseDTO dto = TRIP_TO_RESPONSE.map(trip, TripResponseDTO.class);
        if (trip.getStartDate() != null && trip.getEndDate() != null) {
            dto.setStatus(TripStatus.fromDates(trip.getStartDate(), trip.getEndDate(), LocalDate.now()));
        } else {
            dto.setStatus(trip.getStatus() != null ? trip.getStatus() : TripStatus.PLANNING);
        }
        if (trip.getCreatedBy() != null) {
            dto.setCreatedBy(trip.getCreatedBy().id);
        }
        if (trip.getWorkspace() != null) {
            dto.setWorkspaceId(trip.getWorkspace().id);
        }
        if (trip.getAgency() != null) {
            dto.setAgencyId(trip.getAgency().id);
        }
        dto.setProposalStatus(trip.getProposalStatus());
        dto.setBaseCost(trip.getBaseCost());
        dto.setFinalPrice(trip.getFinalPrice());
        dto.setShareCode(trip.getShareCode());
        dto.setCurrency(trip.getCurrency());
        dto.setProposalClientEmail(trip.getProposalClientEmail());
        dto.setProposalClientName(trip.getProposalClientName());
        dto.setProposalSentAt(trip.getProposalSentAt());
        java.util.Set<TripUnlockKind> unlocks = unlockKinds != null ? unlockKinds : java.util.Set.<TripUnlockKind>of();
        dto.setUnlockedExportPdf(unlocks.contains(TripUnlockKind.EXPORT_PDF));
        dto.setUnlockedAi(unlocks.contains(TripUnlockKind.AI_GENERATIONS));
        dto.setUnlocked(!unlocks.isEmpty());
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
