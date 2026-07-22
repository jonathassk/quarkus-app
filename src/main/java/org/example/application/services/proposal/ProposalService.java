package org.example.application.services.proposal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.agency.AgencyAnalyticsDTO;
import org.example.application.dto.proposal.*;
import org.example.application.services.B2bAuditService;
import org.example.application.services.agency.AgencyService;
import org.example.domain.entity.*;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.DocumentVisibility;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.TripProposalTierRepository;
import org.example.domain.repository.TripRepository;
import org.example.infrastructure.mapper.TripMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class ProposalService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] SHARE_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    @Inject
    TripRepository tripRepository;
    @Inject
    TripProposalTierRepository tierRepository;
    @Inject
    AgencyMemberRepository agencyMemberRepository;
    @Inject
    AgencyService agencyService;
    @Inject
    B2bAuditService auditService;

    public static String generateShareCode() {
        char[] buf = new char[12];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = SHARE_ALPHABET[RANDOM.nextInt(SHARE_ALPHABET.length)];
        }
        return new String(buf);
    }

    public PublicProposalDTO getPublicProposal(String shareCode) {
        Trip trip = tripRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        return toPublicDto(trip);
    }

    @Transactional
    public PublicProposalDTO approvePublicProposal(String shareCode) {
        Trip trip = tripRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        trip.setProposalStatus(ProposalStatus.APPROVED);
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);
        auditService.record(
                trip,
                trip.getCreatedBy() != null ? trip.getCreatedBy().id : null,
                B2bTripLogAction.PROPOSAL_APPROVED,
                "TRIP",
                trip.id,
                null,
                "{\"proposalStatus\":\"APPROVED\"}",
                "Proposta aprovada pelo cliente via Magic Link",
                null);
        return toPublicDto(trip);
    }

    @Transactional
    public Trip updatePricing(UUID tripId, UUID userId, UpdateTripPricingRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (request.getBaseCost() == null) {
            throw new BadRequestException("baseCost is required");
        }
        BigDecimal base = request.getBaseCost();
        BigDecimal markup = request.getMarkupPercentage();
        if (markup == null && trip.getAgency() != null && trip.getAgency().getMarkupPercentage() != null) {
            markup = trip.getAgency().getMarkupPercentage();
        }
        if (markup == null) {
            markup = BigDecimal.ZERO;
        }
        BigDecimal finalPrice = base
                .multiply(BigDecimal.ONE.add(markup.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        trip.setBaseCost(base);
        trip.setFinalPrice(finalPrice);
        tripRepository.persist(trip);
        auditService.record(
                trip, userId, B2bTripLogAction.PROPOSAL_PRICING_UPDATED,
                "TRIP", trip.id, null,
                "{\"baseCost\":" + base + ",\"finalPrice\":" + finalPrice + "}",
                "Preço da proposta atualizado", null);
        return trip;
    }

    @Transactional
    public List<ProposalTierDTO> upsertTiers(UUID tripId, UUID userId, UpsertProposalTiersRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (request.getTiers() == null) {
            throw new BadRequestException("tiers is required");
        }
        tierRepository.deleteByTripId(tripId);
        if (trip.getProposalTiers() != null) {
            trip.getProposalTiers().clear();
        }
        List<ProposalTierDTO> result = new ArrayList<>();
        int order = 0;
        for (UpsertProposalTiersRequest.TierItem item : request.getTiers()) {
            if (item.getCode() == null || item.getCode().isBlank()
                    || item.getLabel() == null || item.getLabel().isBlank()) {
                continue;
            }
            TripProposalTier tier = TripProposalTier.builder()
                    .trip(trip)
                    .code(item.getCode().trim())
                    .label(item.getLabel().trim())
                    .priceDelta(item.getPriceDelta() != null ? item.getPriceDelta() : BigDecimal.ZERO)
                    .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : order)
                    .build();
            tierRepository.persist(tier);
            result.add(toTierDto(tier));
            order++;
        }
        auditService.record(
                trip, userId, B2bTripLogAction.PROPOSAL_TIERS_UPDATED,
                "TRIP", trip.id, null, null,
                "Tiers da proposta atualizados (" + result.size() + ")", null);
        return result;
    }

    @Transactional
    public Trip sendProposal(UUID tripId, UUID userId) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (trip.getShareCode() == null || trip.getShareCode().isBlank()) {
            trip.setShareCode(generateUniqueShareCode());
        }
        trip.setProposalStatus(ProposalStatus.SENT);
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);
        auditService.record(
                trip, userId, B2bTripLogAction.PROPOSAL_SENT,
                "TRIP", trip.id, null, null,
                "Proposta enviada ao cliente", null);
        return trip;
    }

    @Transactional
    public Trip updateProposalStatus(UUID tripId, UUID userId, ProposalStatus status) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (status == null) {
            throw new BadRequestException("proposalStatus is required");
        }
        trip.setProposalStatus(status);
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);
        B2bTripLogAction action = switch (status) {
            case APPROVED -> B2bTripLogAction.PROPOSAL_APPROVED;
            case REJECTED, LOST -> B2bTripLogAction.PROPOSAL_REJECTED;
            case SENT -> B2bTripLogAction.PROPOSAL_SENT;
            default -> B2bTripLogAction.TRIP_STATUS_CHANGED;
        };
        auditService.record(trip, userId, action, "TRIP", trip.id, null,
                "{\"proposalStatus\":\"" + status + "\"}",
                "Status da proposta alterado para " + status, null);
        return trip;
    }

    public List<PipelineTripCardDTO> listPipeline(UUID userId) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        Agency agency = member.getAgency();
        List<Trip> trips = tripRepository.findByAgencyId(agency.id);
        if (member.getAgencyRole() != AgencyRole.AGENCY_OWNER) {
            trips = trips.stream()
                    .filter(t -> t.getCreatedBy() != null && t.getCreatedBy().id.equals(userId))
                    .toList();
        }
        return trips.stream().map(this::toPipelineCard).toList();
    }

    public AgencyAnalyticsDTO analytics(UUID userId) {
        AgencyMember member = agencyService.requireOwner(userId);
        List<Trip> trips = tripRepository.findByAgencyId(member.getAgency().id);

        long draft = 0, sent = 0, approved = 0, rejected = 0, lost = 0;
        BigDecimal forecast = BigDecimal.ZERO;
        Map<String, Long> destinations = new LinkedHashMap<>();

        for (Trip t : trips) {
            ProposalStatus s = t.getProposalStatus() != null ? t.getProposalStatus() : ProposalStatus.DRAFT;
            switch (s) {
                case DRAFT -> draft++;
                case SENT -> sent++;
                case APPROVED -> {
                    approved++;
                    if (t.getFinalPrice() != null) {
                        forecast = forecast.add(t.getFinalPrice());
                    }
                }
                case REJECTED -> rejected++;
                case LOST -> lost++;
            }
            String dest = t.getName() != null ? t.getName() : "—";
            if (t.getSegments() != null && !t.getSegments().isEmpty()
                    && t.getSegments().get(0).getCityId() != null) {
                dest = t.getSegments().get(0).getCityId();
            }
            destinations.merge(dest, 1L, Long::sum);
        }

        long conversionDenom = sent + approved + rejected + lost;
        double conversion = conversionDenom == 0 ? 0.0 : (approved * 100.0) / conversionDenom;

        List<AgencyAnalyticsDTO.DestinationStat> top = destinations.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> AgencyAnalyticsDTO.DestinationStat.builder()
                        .cityOrName(e.getKey()).count(e.getValue()).build())
                .toList();

        return AgencyAnalyticsDTO.builder()
                .proposalsDraft(draft)
                .proposalsSent(sent)
                .proposalsApproved(approved)
                .proposalsRejected(rejected)
                .proposalsLost(lost)
                .conversionRate(Math.round(conversion * 100.0) / 100.0)
                .forecastRevenue(forecast)
                .topDestinations(top)
                .build();
    }

    private PublicProposalDTO toPublicDto(Trip trip) {
        Agency agency = trip.getAgency();
        List<ProposalTierDTO> tiers = tierRepository.findByTripId(trip.id).stream()
                .map(this::toTierDto)
                .toList();

        List<PublicProposalDTO.PublicDocumentDTO> docs = new ArrayList<>();
        if (trip.id != null) {
            List<TripDocument> documents = TripDocument.list("trip.id = ?1 AND status = ?2 AND visibility = ?3",
                    trip.id, org.example.domain.enums.DocumentStatus.READY, DocumentVisibility.CLIENT);
            for (TripDocument d : documents) {
                docs.add(PublicProposalDTO.PublicDocumentDTO.builder()
                        .id(d.id)
                        .title(d.getTitle())
                        .contentType(d.getContentType())
                        .activityId(d.getActivity() != null ? d.getActivity().id : null)
                        .segmentId(d.getSegment() != null ? d.getSegment().id : null)
                        .build());
            }
        }

        var response = TripMapper.mapToTripResponseDTO(trip);
        return PublicProposalDTO.builder()
                .shareCode(trip.getShareCode())
                .tripId(trip.id)
                .name(trip.getName())
                .description(trip.getDescription())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .durationDays(trip.getDurationDays())
                .coverImageUrl(trip.getCoverImageUrl())
                .currency(trip.getCurrency())
                .finalPrice(trip.getFinalPrice())
                .proposalStatus(trip.getProposalStatus())
                .agency(agency != null ? agencyService.toPublicBrandingDto(agency) : null)
                .segments(response.getSegments())
                .tiers(tiers)
                .documents(docs)
                .build();
    }

    private ProposalTierDTO toTierDto(TripProposalTier tier) {
        return ProposalTierDTO.builder()
                .id(tier.id)
                .code(tier.getCode())
                .label(tier.getLabel())
                .priceDelta(tier.getPriceDelta())
                .sortOrder(tier.getSortOrder())
                .build();
    }

    private PipelineTripCardDTO toPipelineCard(Trip t) {
        return PipelineTripCardDTO.builder()
                .tripId(t.id)
                .name(t.getName())
                .shareCode(t.getShareCode())
                .proposalStatus(t.getProposalStatus())
                .finalPrice(t.getFinalPrice())
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .lastContactAt(t.getLastContactAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy() != null ? t.getCreatedBy().id : null)
                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : null)
                .build();
    }

    private Trip requireAgencyTripAccess(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            throw new NotFoundException("Trip not found");
        }
        if (trip.getAgency() == null) {
            throw new ForbiddenException("Trip is not linked to an agency");
        }
        AgencyMember member = agencyMemberRepository
                .findByAgencyAndUser(trip.getAgency().id, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this agency"));
        if (member.getAgencyRole() == AgencyRole.AGENCY_OWNER) {
            return trip;
        }
        if (trip.getCreatedBy() != null && trip.getCreatedBy().id.equals(userId)) {
            return trip;
        }
        if (tripRepository.isUserLinkedToTrip(tripId, userId)) {
            return trip;
        }
        throw new ForbiddenException("No access to this trip");
    }

    private String generateUniqueShareCode() {
        for (int i = 0; i < 8; i++) {
            String code = generateShareCode();
            if (tripRepository.findByShareCode(code).isEmpty()) {
                return code;
            }
        }
        return generateShareCode() + Long.toString(System.currentTimeMillis(), 36);
    }
}
