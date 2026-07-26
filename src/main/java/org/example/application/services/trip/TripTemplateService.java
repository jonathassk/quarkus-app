package org.example.application.services.trip;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.template.SaveAsTemplateRequest;
import org.example.application.dto.template.TripTemplateDTO;
import org.example.application.dto.trip.TripSegmentDTO;
import org.example.application.dto.trip.TripUserDTO;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.services.agency.AgencyService;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripSegment;
import org.example.domain.entity.TripTemplate;
import org.example.domain.entity.User;
import org.example.domain.enums.TripTemplateKind;
import org.example.domain.enums.TripTemplateScope;
import org.example.domain.enums.UserPermissionLevel;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.TripSegmentRepository;
import org.example.domain.repository.TripTemplateRepository;
import org.example.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class TripTemplateService {

    @Inject
    TripTemplateRepository templateRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripSegmentRepository tripSegmentRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    AgencyService agencyService;
    @Inject
    CreateTripUseCase createTripUseCase;
    @Inject
    ObjectMapper objectMapper;

    public List<TripTemplateDTO> list(UUID userId, TripTemplateKind kind) {
        List<TripTemplateDTO> out = new ArrayList<>();
        templateRepository.findPersonal(userId, kind).forEach(t -> out.add(toDto(t)));
        agencyService.requireMembership(userId).ifPresent(member ->
                templateRepository.findAgency(member.getAgency().id, kind)
                        .forEach(t -> out.add(toDto(t))));
        return out;
    }

    public TripTemplateDTO get(UUID userId, UUID templateId) {
        return toDto(requireAccessible(userId, templateId));
    }

    @Transactional
    public TripTemplateDTO saveFromTrip(UUID tripId, UUID userId, SaveAsTemplateRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        if (!canAccessTrip(trip, userId)) {
            throw new ForbiddenException("No access to this trip");
        }

        TripTemplateScope scope = request.getScope() != null ? request.getScope() : TripTemplateScope.PERSONAL;
        TripTemplateKind kind = request.getKind() != null ? request.getKind() : TripTemplateKind.FULL_TRIP;

        UUID agencyId = null;
        UUID ownerId = userId;
        if (scope == TripTemplateScope.AGENCY) {
            AgencyMember member = agencyService.requireMembershipOrThrow(userId);
            agencyId = member.getAgency().id;
            ownerId = null;
        }

        String payload;
        try {
            if (kind == TripTemplateKind.SEGMENT_BLOCK) {
                if (request.getSegmentId() == null) {
                    throw new BadRequestException("segmentId is required for SEGMENT_BLOCK");
                }
                TripSegment segment = tripSegmentRepository.findById(request.getSegmentId());
                if (segment == null || segment.getTrip() == null || !segment.getTrip().id.equals(tripId)) {
                    throw new NotFoundException("Segment not found");
                }
                payload = objectMapper.writeValueAsString(TripItineraryService.toDto(segment));
            } else {
                payload = objectMapper.writeValueAsString(toTripRequestSnapshot(trip));
            }
        } catch (BadRequestException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to serialize template payload");
        }

        TripTemplate template = TripTemplate.builder()
                .scope(scope)
                .kind(kind)
                .ownerId(ownerId)
                .agencyId(agencyId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .payload(payload)
                .build();
        templateRepository.persist(template);
        return toDto(template);
    }

    @Transactional
    public Trip createFromTemplate(UUID templateId, UUID userId) {
        TripTemplate template = requireAccessible(userId, templateId);
        if (template.getKind() == TripTemplateKind.SEGMENT_BLOCK) {
            throw new BadRequestException(
                    "SEGMENT_BLOCK não cria viagem sozinho — use na edição do roteiro");
        }
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        try {
            TripRequestDTO snapshot = objectMapper.readValue(template.getPayload(), TripRequestDTO.class);
            snapshot.setCreatedBy(userId);
            snapshot.setName(template.getName());
            if (snapshot.getUsers() == null || snapshot.getUsers().isEmpty()) {
                snapshot.setUsers(List.of(TripUserDTO.builder()
                        .userId(userId)
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .permissionLevel(UserPermissionLevel.OWNER)
                        .build()));
            } else {
                snapshot.getUsers().forEach(u -> {
                    u.setUserId(userId);
                    u.setEmail(user.getEmail());
                    u.setFullName(user.getFullName());
                    u.setPermissionLevel(UserPermissionLevel.OWNER);
                });
            }
            return createTripUseCase.createTrip(snapshot);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create trip from template {}", templateId, e);
            throw new BadRequestException("Template payload inválido");
        }
    }

    @Transactional
    public void delete(UUID userId, UUID templateId) {
        TripTemplate template = requireAccessible(userId, templateId);
        if (template.getScope() == TripTemplateScope.PERSONAL
                && template.getOwnerId() != null
                && !template.getOwnerId().equals(userId)) {
            throw new ForbiddenException("Cannot delete this template");
        }
        if (template.getScope() == TripTemplateScope.AGENCY) {
            agencyService.requireOwner(userId);
        }
        templateRepository.delete(template);
    }

    private TripTemplate requireAccessible(UUID userId, UUID templateId) {
        TripTemplate template = templateRepository.findById(templateId);
        if (template == null) {
            throw new NotFoundException("Template not found");
        }
        if (template.getScope() == TripTemplateScope.PERSONAL) {
            if (template.getOwnerId() == null || !template.getOwnerId().equals(userId)) {
                throw new ForbiddenException("No access to this template");
            }
            return template;
        }
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        if (template.getAgencyId() == null || !template.getAgencyId().equals(member.getAgency().id)) {
            throw new ForbiddenException("No access to this template");
        }
        return template;
    }

    private boolean canAccessTrip(Trip trip, UUID userId) {
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) {
            return true;
        }
        return tripRepository.isUserLinkedToTrip(trip.id, userId);
    }

    private TripRequestDTO toTripRequestSnapshot(Trip trip) {
        List<TripSegmentDTO> segments = new ArrayList<>();
        if (trip.getSegments() != null) {
            for (TripSegment s : trip.getSegments()) {
                segments.add(TripItineraryService.toDto(s));
            }
        }
        return TripRequestDTO.builder()
                .name(trip.getName())
                .description(trip.getDescription())
                .budgetTotal(trip.getBudgetTotal())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .durationDays(trip.getDurationDays())
                .targetMonth(trip.getTargetMonth())
                .coverImageUrl(trip.getCoverImageUrl())
                .visibility(trip.getVisibility())
                .segments(segments)
                .build();
    }

    private TripTemplateDTO toDto(TripTemplate t) {
        return TripTemplateDTO.builder()
                .id(t.id)
                .scope(t.getScope())
                .kind(t.getKind())
                .ownerId(t.getOwnerId())
                .agencyId(t.getAgencyId())
                .name(t.getName())
                .description(t.getDescription())
                .payload(t.getPayload())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
