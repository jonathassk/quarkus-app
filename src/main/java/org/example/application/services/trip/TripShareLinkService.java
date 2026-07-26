package org.example.application.services.trip;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.trip.request.CreateTripShareLinkRequest;
import org.example.application.dto.trip.response.PublicTripDTO;
import org.example.application.dto.trip.response.TripShareLinkDTO;
import org.example.application.services.TripCollaborationService;
import org.example.application.services.proposal.ProposalService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripShareLink;
import org.example.domain.entity.User;
import org.example.domain.enums.TripShareLinkScope;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.TripShareLinkRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TripShareLinkService {

    private final TripRepository tripRepository;
    private final TripShareLinkRepository shareLinkRepository;
    private final UserRepository userRepository;
    private final TripCollaborationService collaborationService;
    private final org.example.application.services.entitlement.EntitlementService entitlementService;

    @ConfigProperty(name = "app.public-url")
    String appPublicUrl;

    @Transactional
    public TripShareLinkDTO createOrRotate(UUID tripId, UUID actorId, CreateTripShareLinkRequest request) {
        Trip trip = requireManageableTrip(tripId, actorId);
        entitlementService.requireCanCreateShareLink(actorId, tripId);
        Instant expiresAt = request != null ? request.getExpiresAt() : null;
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new BadRequestException("expiresAt must be in the future");
        }

        Instant now = Instant.now();
        for (TripShareLink existing : shareLinkRepository.findActiveByTripId(tripId)) {
            existing.setRevokedAt(now);
            shareLinkRepository.persist(existing);
        }

        User creator = userRepository.findById(actorId);
        TripShareLink link =
                TripShareLink.builder()
                        .trip(trip)
                        .code(generateUniqueCode())
                        .scope(TripShareLinkScope.VIEW_ONLY)
                        .expiresAt(expiresAt)
                        .createdBy(creator)
                        .build();
        shareLinkRepository.persist(link);
        log.info("Created trip share link tripId={} code={}", tripId, link.getCode());
        return toDto(link);
    }

    public List<TripShareLinkDTO> list(UUID tripId, UUID actorId) {
        requireManageableTrip(tripId, actorId);
        return shareLinkRepository.findByTripId(tripId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void revoke(UUID tripId, UUID linkId, UUID actorId) {
        requireManageableTrip(tripId, actorId);
        TripShareLink link =
                shareLinkRepository
                        .findByIdOptional(linkId)
                        .filter(l -> l.getTrip() != null && tripId.equals(l.getTrip().id))
                        .orElseThrow(() -> new NotFoundException("Share link not found"));
        if (link.getRevokedAt() == null) {
            link.setRevokedAt(Instant.now());
            shareLinkRepository.persist(link);
        }
    }

    public PublicTripDTO getPublicByCode(String code) {
        TripShareLink link =
                shareLinkRepository
                        .findByCode(code)
                        .orElseThrow(() -> new NotFoundException("Trip not found"));
        if (!link.isActive()) {
            throw new NotFoundException("Trip not found");
        }
        Trip trip = link.getTrip();
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        var response = TripMapper.mapToTripResponseDTO(trip);
        return PublicTripDTO.builder()
                .code(link.getCode())
                .tripId(trip.id)
                .name(trip.getName())
                .description(trip.getDescription())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .durationDays(trip.getDurationDays())
                .coverImageUrl(trip.getCoverImageUrl())
                .currency(trip.getCurrency())
                .segments(response.getSegments())
                .build();
    }

    private Trip requireManageableTrip(UUID tripId, UUID actorId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        collaborationService.requireCanManageMembers(trip, actorId);
        return trip;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 8; i++) {
            String code = ProposalService.generateShareCode();
            if (shareLinkRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        return ProposalService.generateShareCode() + Long.toString(System.currentTimeMillis(), 36);
    }

    private TripShareLinkDTO toDto(TripShareLink link) {
        return TripShareLinkDTO.builder()
                .id(link.id)
                .tripId(link.getTrip() != null ? link.getTrip().id : null)
                .code(link.getCode())
                .scope(link.getScope())
                .expiresAt(link.getExpiresAt())
                .revokedAt(link.getRevokedAt())
                .createdAt(link.getCreatedAt())
                .url(publicUrl(link.getCode()))
                .active(link.isActive())
                .build();
    }

    private String publicUrl(String code) {
        String base = appPublicUrl != null ? appPublicUrl.trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/t/" + code;
    }
}
