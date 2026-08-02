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
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.*;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.DocumentVisibility;
import org.example.domain.enums.NotificationKind;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.repository.AgencyClientRepository;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.ProposalAcceptanceRepository;
import org.example.domain.repository.TripProposalTierRepository;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class ProposalService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] SHARE_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    /** Ator das ações feitas sem sessão, pelo link público da proposta. */
    private static final String PUBLIC_CLIENT_ACTOR_LABEL = "Cliente (link público)";
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    private static final int DEFAULT_PROPOSAL_TTL_DAYS = 7;

    @Inject
    TripRepository tripRepository;
    @Inject
    TripProposalTierRepository tierRepository;
    @Inject
    ProposalAcceptanceRepository acceptanceRepository;
    @Inject
    AgencyMemberRepository agencyMemberRepository;
    @Inject
    AgencyClientRepository agencyClientRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    AgencyService agencyService;
    @Inject
    B2bAuditService auditService;
    @Inject
    org.example.infrastructure.email.EmailWorkerInvoker emailWorkerInvoker;
    @Inject
    NotificationService notificationService;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "app.public-url")
    String appPublicUrl;

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
    public PublicProposalDTO approvePublicProposal(
            String shareCode,
            ApprovePublicProposalRequest request,
            String clientIp,
            String userAgent) {
        Trip trip = tripRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        assertProposalActionable(trip);

        if (request == null
                || request.getName() == null || request.getName().isBlank()
                || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BadRequestException("name and email are required to accept the proposal");
        }
        String name = request.getName().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("email is invalid");
        }

        List<String> tierCodes = normalizeTierCodes(request.getTierCodes(), trip);
        String tierCodesJoined = tierCodes.isEmpty() ? null : String.join(",", tierCodes);

        ProposalAcceptance acceptance = ProposalAcceptance.builder()
                .trip(trip)
                .name(name)
                .email(email)
                .ip(truncate(clientIp, 64))
                .userAgent(truncate(userAgent, 512))
                .acceptedAt(Instant.now())
                .tierCodes(tierCodesJoined)
                .build();
        acceptanceRepository.persist(acceptance);

        trip.setProposalClientName(name);
        trip.setProposalClientEmail(email);

        // Recalcula finalPrice se tiers foram escolhidos (base + deltas).
        if (!tierCodes.isEmpty() && trip.getFinalPrice() != null) {
            BigDecimal delta = tierRepository.findByTripId(trip.id).stream()
                    .filter(t -> tierCodes.contains(t.getCode()))
                    .map(t -> t.getPriceDelta() != null ? t.getPriceDelta() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trip.setFinalPrice(trip.getFinalPrice().add(delta).setScale(2, RoundingMode.HALF_UP));
        }

        ProposalStatus next = ProposalStatus.APPROVED;
        if (trip.getFinalPrice() != null && trip.getFinalPrice().compareTo(BigDecimal.ZERO) > 0) {
            next = ProposalStatus.PENDING_PAYMENT;
        }

        trip.setProposalStatus(next);
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);

        String actorLabel = name + " <" + email + ">";
        String meta = "{\"proposalStatus\":\"" + next + "\""
                + ",\"acceptanceId\":\"" + acceptance.id + "\""
                + (tierCodesJoined != null ? ",\"tierCodes\":\"" + tierCodesJoined + "\"" : "")
                + "}";
        auditService.recordExternalActor(
                trip,
                actorLabel,
                next == ProposalStatus.PENDING_PAYMENT
                        ? B2bTripLogAction.PROPOSAL_PAYMENT_PENDING
                        : B2bTripLogAction.PROPOSAL_APPROVED,
                "TRIP",
                trip.id,
                null,
                meta,
                next == ProposalStatus.PENDING_PAYMENT
                        ? "Proposta aprovada por " + actorLabel + " — aguardando pagamento"
                        : "Proposta aprovada por " + actorLabel
                        + (tierCodesJoined != null ? " (tiers: " + tierCodesJoined + ")" : ""),
                null);
        notifyAgencyOfProposalApproved(trip);
        return toPublicDto(trip);
    }

    @Transactional
    public PublicProposalDTO rejectPublicProposal(
            String shareCode,
            RejectPublicProposalRequest request,
            String clientIp,
            String userAgent) {
        Trip trip = tripRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        assertProposalActionable(trip);

        String reason = request != null && request.getReason() != null
                ? request.getReason().trim()
                : "";
        if (reason.isBlank()) {
            throw new BadRequestException("reason is required to reject the proposal");
        }
        if (reason.length() > 2000) {
            reason = reason.substring(0, 2000);
        }

        String name = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase(Locale.ROOT)
                : null;
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("email is invalid");
        }

        trip.setProposalStatus(ProposalStatus.REJECTED);
        trip.setProposalRejectReason(reason);
        trip.setLastContactAt(Instant.now());
        if (name != null) {
            trip.setProposalClientName(name);
        }
        if (email != null) {
            trip.setProposalClientEmail(email);
        }
        tripRepository.persist(trip);

        String actorLabel = name != null && email != null
                ? name + " <" + email + ">"
                : PUBLIC_CLIENT_ACTOR_LABEL;
        auditService.recordExternalActor(
                trip,
                actorLabel,
                B2bTripLogAction.PROPOSAL_REJECTED,
                "TRIP",
                trip.id,
                null,
                "{\"proposalStatus\":\"REJECTED\",\"ip\":\"" + truncate(clientIp, 64) + "\"}",
                "Proposta recusada: " + reason,
                null);
        return toPublicDto(trip);
    }

    private void assertProposalActionable(Trip trip) {
        if (isExpired(trip)) {
            throw new BadRequestException("Esta proposta expirou e não pode mais ser aceita ou recusada");
        }
        ProposalStatus status = trip.getProposalStatus() != null ? trip.getProposalStatus() : ProposalStatus.DRAFT;
        if (status == ProposalStatus.REJECTED
                || status == ProposalStatus.LOST
                || status == ProposalStatus.CONFIRMED) {
            throw new BadRequestException("Proposta já está em status final: " + status);
        }
        if (status == ProposalStatus.APPROVED || status == ProposalStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Proposta já foi aprovada");
        }
        if (status != ProposalStatus.SENT && status != ProposalStatus.DRAFT) {
            throw new BadRequestException("Proposta não está disponível para esta ação (status: " + status + ")");
        }
    }

    static boolean isExpired(Trip trip) {
        return trip.getProposalExpiresAt() != null
                && trip.getProposalExpiresAt().isBefore(Instant.now());
    }

    private List<String> normalizeTierCodes(List<String> requested, Trip trip) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        var valid = tierRepository.findByTripId(trip.id).stream()
                .map(TripProposalTier::getCode)
                .collect(Collectors.toSet());
        List<String> out = new ArrayList<>();
        for (String code : requested) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String c = code.trim();
            if (!valid.contains(c)) {
                throw new BadRequestException("Unknown tier code: " + c);
            }
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private void notifyAgencyOfProposalApproved(Trip trip) {
        List<UUID> recipients = resolveAgencyRecipients(trip, null);
        if (recipients.isEmpty()) {
            return;
        }
        String tripName = trip.getName() != null ? trip.getName() : "proposta";
        notificationService.createForUsers(
                recipients,
                NotificationKind.PROPOSAL_APPROVED,
                "Proposta aprovada: " + tripName,
                "O cliente aprovou a proposta \"" + tripName + "\".",
                "TRIP",
                trip.id,
                true);
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
    public Trip sendProposal(UUID tripId, UUID userId, SendProposalRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (trip.getShareCode() == null || trip.getShareCode().isBlank()) {
            trip.setShareCode(generateUniqueShareCode());
        }

        String clientEmail = resolveClientEmail(trip, request);
        if (request != null && request.getClientName() != null && !request.getClientName().isBlank()) {
            trip.setProposalClientName(request.getClientName().trim());
        }
        trip.setProposalClientEmail(clientEmail);

        Instant now = Instant.now();
        trip.setProposalStatus(ProposalStatus.SENT);
        trip.setLastContactAt(now);
        trip.setProposalSentAt(now);
        trip.setProposalExpiresAt(resolveExpiry(request, now));
        trip.setProposalRejectReason(null);
        tripRepository.persist(trip);

        // Upsert CRM leve a partir do contato da proposta.
        ensureClientLinked(trip, clientEmail, trip.getProposalClientName());

        boolean queued = emailWorkerInvoker.enqueueWhiteLabelEmail(
                clientEmail,
                trip.getAgency() != null ? trip.getAgency().id.toString() : null,
                "proposal_sent",
                trip.getName(),
                publicProposalUrl(trip.getShareCode()));
        if (!queued) {
            log.warn("Proposal e-mail not queued for tripId={} to={}", trip.id, clientEmail);
        }

        auditService.record(
                trip, userId, B2bTripLogAction.PROPOSAL_SENT,
                "TRIP", trip.id, null,
                "{\"proposalStatus\":\"SENT\",\"emailQueued\":" + queued + "}",
                "Proposta enviada para " + clientEmail, null);

        // In-app para membros internos; e-mail do cliente já foi via white-label.
        List<UUID> internalRecipients = resolveAgencyRecipients(trip, userId);
        if (!internalRecipients.isEmpty()) {
            String tripName = trip.getName() != null ? trip.getName() : "proposta";
            notificationService.createForUsers(
                    internalRecipients,
                    NotificationKind.PROPOSAL_SENT,
                    "Proposta enviada: " + tripName,
                    "Proposta enviada para " + clientEmail,
                    "TRIP",
                    trip.id,
                    false);
        }
        return trip;
    }

    private List<UUID> resolveAgencyRecipients(Trip trip, UUID excludeUserId) {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        if (trip.getCreatedBy() != null) {
            ids.add(trip.getCreatedBy().id);
        }
        if (trip.getAgency() != null) {
            for (AgencyMember member : agencyMemberRepository.findAllByAgency(trip.getAgency().id)) {
                if (member.getUser() != null) {
                    ids.add(member.getUser().id);
                }
            }
        }
        if (excludeUserId != null) {
            ids.remove(excludeUserId);
        }
        return new ArrayList<>(ids);
    }

    private Instant resolveExpiry(SendProposalRequest request, Instant now) {
        if (request != null && request.getProposalExpiresAt() != null) {
            if (!request.getProposalExpiresAt().isAfter(now)) {
                throw new BadRequestException("proposalExpiresAt must be in the future");
            }
            return request.getProposalExpiresAt();
        }
        int days = DEFAULT_PROPOSAL_TTL_DAYS;
        if (request != null && request.getExpiresInDays() != null) {
            days = Math.min(Math.max(request.getExpiresInDays(), 1), 90);
        }
        return now.plus(Duration.ofDays(days));
    }

    private void ensureClientLinked(Trip trip, String email, String name) {
        if (trip.getAgency() == null || email == null || email.isBlank()) {
            return;
        }
        if (trip.getClient() != null) {
            return;
        }
        AgencyClient existing = agencyClientRepository
                .findByAgencyAndEmail(trip.getAgency().id, email)
                .orElse(null);
        if (existing == null) {
            existing = AgencyClient.builder()
                    .agency(trip.getAgency())
                    .name(name != null && !name.isBlank() ? name.trim() : email)
                    .email(email.trim().toLowerCase(Locale.ROOT))
                    .build();
            agencyClientRepository.persist(existing);
        }
        trip.setClient(existing);
        tripRepository.persist(trip);
    }

    private String resolveClientEmail(Trip trip, SendProposalRequest request) {
        String requested = request != null && request.getClientEmail() != null
                ? request.getClientEmail().trim()
                : null;
        String email = requested != null && !requested.isBlank() ? requested : trip.getProposalClientEmail();
        if (email == null || email.isBlank()) {
            throw new BadRequestException("clientEmail is required to send the proposal");
        }
        email = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("clientEmail is invalid");
        }
        return email;
    }

    private String publicProposalUrl(String shareCode) {
        String base = appPublicUrl != null ? appPublicUrl.trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/p/" + shareCode;
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
            case APPROVED, PENDING_PAYMENT -> B2bTripLogAction.PROPOSAL_APPROVED;
            case CONFIRMED -> B2bTripLogAction.PROPOSAL_CONFIRMED;
            case REJECTED, LOST -> B2bTripLogAction.PROPOSAL_REJECTED;
            case SENT -> B2bTripLogAction.PROPOSAL_SENT;
            default -> B2bTripLogAction.TRIP_STATUS_CHANGED;
        };
        auditService.record(trip, userId, action, "TRIP", trip.id, null,
                "{\"proposalStatus\":\"" + status + "\"}",
                "Status da proposta alterado para " + status, null);
        return trip;
    }

    @Transactional
    public Trip assignConsultant(UUID tripId, UUID actorUserId, AssignTripConsultantRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, actorUserId);
        AgencyMember actor = agencyMemberRepository
                .findByAgencyAndUser(trip.getAgency().id, actorUserId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this agency"));
        if (actor.getAgencyRole() != AgencyRole.AGENCY_OWNER) {
            throw new ForbiddenException("Only agency owners can reassign trips");
        }

        User consultant = null;
        if (request != null && request.getConsultantId() != null) {
            consultant = userRepository.findById(request.getConsultantId());
            if (consultant == null) {
                throw new NotFoundException("Consultant not found");
            }
            agencyMemberRepository
                    .findByAgencyAndUser(trip.getAgency().id, consultant.id)
                    .orElseThrow(() -> new BadRequestException("User is not a member of this agency"));
        }
        trip.setAssignedConsultant(consultant);
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);
        auditService.record(
                trip, actorUserId, B2bTripLogAction.TRIP_ASSIGNED,
                "TRIP", trip.id, null,
                consultant != null
                        ? "{\"consultantId\":\"" + consultant.id + "\"}"
                        : "{\"consultantId\":null}",
                consultant != null
                        ? "Viagem atribuída a " + consultant.getFullName()
                        : "Atribuição de consultor removida",
                null);
        return trip;
    }

    @Transactional
    public Trip linkClient(UUID tripId, UUID userId, UUID clientId) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (clientId == null) {
            trip.setClient(null);
            tripRepository.persist(trip);
            return trip;
        }
        AgencyClient client = agencyClientRepository.findById(clientId);
        if (client == null || client.getAgency() == null
                || !client.getAgency().id.equals(trip.getAgency().id)) {
            throw new NotFoundException("Client not found in this agency");
        }
        trip.setClient(client);
        if (trip.getProposalClientEmail() == null || trip.getProposalClientEmail().isBlank()) {
            trip.setProposalClientEmail(client.getEmail());
        }
        if (trip.getProposalClientName() == null || trip.getProposalClientName().isBlank()) {
            trip.setProposalClientName(client.getName());
        }
        tripRepository.persist(trip);
        auditService.record(
                trip, userId, B2bTripLogAction.CLIENT_LINKED,
                "TRIP", trip.id, null,
                "{\"clientId\":\"" + client.id + "\"}",
                "Cliente vinculado: " + client.getName(), null);
        return trip;
    }

    public PipelinePageDTO listPipeline(
            UUID userId,
            ProposalStatus status,
            UUID consultantId,
            String q,
            int page,
            int size) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        Agency agency = member.getAgency();
        UUID scopeUserId = member.getAgencyRole() == AgencyRole.AGENCY_OWNER ? null : userId;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<Trip> trips = tripRepository.findPipeline(
                agency.id, status, consultantId, q, scopeUserId, safePage, safeSize);
        long total = tripRepository.countPipeline(agency.id, status, consultantId, q, scopeUserId);

        return PipelinePageDTO.builder()
                .items(trips.stream().map(this::toPipelineCard).toList())
                .total(total)
                .page(safePage)
                .size(safeSize)
                .build();
    }

    /** Compat: lista plana sem filtros (analytics e callers legados). */
    public List<PipelineTripCardDTO> listPipeline(UUID userId) {
        return listPipeline(userId, null, null, null, 0, 100).getItems();
    }

    @Transactional
    public AgencyAnalyticsDTO analytics(UUID userId) {
        // Qualquer membro da agência vê o BI do portal /business.
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        UUID agencyId = member.getAgency().id;
        List<Trip> trips = tripRepository.findByAgencyId(agencyId);

        long draft = 0, sent = 0, approved = 0, rejected = 0, lost = 0, pendingPayment = 0, confirmed = 0;
        BigDecimal forecast = BigDecimal.ZERO;
        BigDecimal marginSum = BigDecimal.ZERO;
        BigDecimal baseSumForMarkup = BigDecimal.ZERO;
        BigDecimal marginSumForMarkup = BigDecimal.ZERO;
        long pricedVolumeTrips = 0;
        Map<String, Long> destinations = new LinkedHashMap<>();

        for (Trip t : trips) {
            ProposalStatus s = t.getProposalStatus() != null ? t.getProposalStatus() : ProposalStatus.DRAFT;
            boolean countsAsVolume = s == ProposalStatus.APPROVED
                    || s == ProposalStatus.PENDING_PAYMENT
                    || s == ProposalStatus.CONFIRMED;
            switch (s) {
                case DRAFT -> draft++;
                case SENT -> sent++;
                case APPROVED -> approved++;
                case PENDING_PAYMENT -> pendingPayment++;
                case CONFIRMED -> confirmed++;
                case REJECTED -> rejected++;
                case LOST -> lost++;
            }
            if (countsAsVolume && t.getFinalPrice() != null) {
                forecast = forecast.add(t.getFinalPrice());
                pricedVolumeTrips++;
                if (t.getBaseCost() != null) {
                    BigDecimal tripMargin = t.getFinalPrice().subtract(t.getBaseCost());
                    marginSum = marginSum.add(tripMargin);
                    if (t.getBaseCost().compareTo(BigDecimal.ZERO) > 0) {
                        baseSumForMarkup = baseSumForMarkup.add(t.getBaseCost());
                        marginSumForMarkup = marginSumForMarkup.add(tripMargin);
                    }
                }
            }
            String dest = t.getName() != null ? t.getName() : "—";
            if (t.getSegments() != null && !t.getSegments().isEmpty()
                    && t.getSegments().get(0).getCityId() != null) {
                dest = t.getSegments().get(0).getCityId();
            }
            destinations.merge(dest, 1L, Long::sum);
        }

        long conversionDenom = sent + approved + pendingPayment + confirmed + rejected + lost;
        double conversion = conversionDenom == 0 ? 0.0
                : ((approved + pendingPayment + confirmed) * 100.0) / conversionDenom;

        BigDecimal avgPackage = pricedVolumeTrips == 0
                ? BigDecimal.ZERO
                : forecast.divide(BigDecimal.valueOf(pricedVolumeTrips), 2, RoundingMode.HALF_UP);

        Double avgMarginPct = null;
        if (baseSumForMarkup.compareTo(BigDecimal.ZERO) > 0) {
            avgMarginPct = marginSumForMarkup
                    .multiply(BigDecimal.valueOf(100))
                    .divide(baseSumForMarkup, 1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        List<AgencyClient> agencyClients = agencyClientRepository.findByAgencyId(agencyId);
        long memberClients = 0;
        long guestClients = 0;
        for (AgencyClient c : agencyClients) {
            if (c.getUser() != null || hasMemberTag(c.getTags())) {
                memberClients++;
            } else {
                guestClients++;
            }
        }

        List<AgencyAnalyticsDTO.DestinationStat> top = destinations.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> AgencyAnalyticsDTO.DestinationStat.builder()
                        .cityOrName(e.getKey()).count(e.getValue()).build())
                .toList();

        return AgencyAnalyticsDTO.builder()
                .proposalsDraft(draft)
                .proposalsSent(sent)
                .proposalsApproved(approved + pendingPayment + confirmed)
                .proposalsRejected(rejected)
                .proposalsLost(lost)
                .conversionRate(Math.round(conversion * 100.0) / 100.0)
                .forecastRevenue(forecast)
                .grossVolume(forecast)
                .estimatedMargin(marginSum)
                .avgMarginPercentage(avgMarginPct)
                .activeClients(agencyClients.size())
                .memberClients(memberClients)
                .guestClients(guestClients)
                .avgPackagePrice(avgPackage)
                .topDestinations(top)
                .build();
    }

    private static boolean hasMemberTag(String tags) {
        if (tags == null || tags.isBlank()) {
            return false;
        }
        for (String part : tags.split(",")) {
            if ("membro-site".equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
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
                .paymentRequired(trip.getFinalPrice() != null
                        && trip.getFinalPrice().compareTo(BigDecimal.ZERO) > 0
                        && trip.getProposalStatus() != ProposalStatus.CONFIRMED)
                .depositAmount(trip.getFinalPrice() != null && trip.getFinalPrice().compareTo(BigDecimal.ZERO) > 0
                        ? trip.getFinalPrice()
                            .multiply(org.example.application.services.proposal.ProposalPaymentService.DEFAULT_DEPOSIT_RATIO)
                            .setScale(2, RoundingMode.HALF_UP)
                        : null)
                .proposalExpiresAt(trip.getProposalExpiresAt())
                .expired(isExpired(trip))
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
        BigDecimal base = t.getBaseCost();
        BigDecimal finalPrice = t.getFinalPrice();
        BigDecimal margin = null;
        BigDecimal markupPct = null;
        if (base != null && finalPrice != null) {
            margin = finalPrice.subtract(base);
            if (base.compareTo(BigDecimal.ZERO) > 0) {
                markupPct = margin
                        .multiply(new BigDecimal("100"))
                        .divide(base, 2, RoundingMode.HALF_UP);
            }
        }
        return PipelineTripCardDTO.builder()
                .tripId(t.id)
                .name(t.getName())
                .shareCode(t.getShareCode())
                .proposalStatus(t.getProposalStatus())
                .baseCost(base)
                .finalPrice(finalPrice)
                .margin(margin)
                .markupPercentage(markupPct)
                .startDate(t.getStartDate())
                .endDate(t.getEndDate())
                .lastContactAt(t.getLastContactAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy() != null ? t.getCreatedBy().id : null)
                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : null)
                .assignedConsultantId(t.getAssignedConsultant() != null ? t.getAssignedConsultant().id : null)
                .assignedConsultantName(t.getAssignedConsultant() != null
                        ? t.getAssignedConsultant().getFullName() : null)
                .clientId(t.getClient() != null ? t.getClient().id : null)
                .clientName(t.getClient() != null ? t.getClient().getName()
                        : t.getProposalClientName())
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
        if (trip.getAssignedConsultant() != null && trip.getAssignedConsultant().id.equals(userId)) {
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
