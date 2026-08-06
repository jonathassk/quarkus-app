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
import org.example.application.services.agency.AgencyOpportunityService;
import org.example.application.services.agency.AgencyService;
import org.example.application.services.agency.OpportunityTaskAutomationService;
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.*;
import org.example.domain.enums.AgencyRole;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.DocumentVisibility;
import org.example.domain.enums.NotificationKind;
import org.example.domain.enums.OperationStatus;
import org.example.domain.enums.OpportunityActivityType;
import org.example.domain.enums.PipelineScope;
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
import java.time.LocalDate;
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
    AgencyOpportunityService agencyOpportunityService;
    @Inject
    OpportunityTaskAutomationService taskAutomationService;
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

    @Transactional
    public PublicProposalDTO getPublicProposal(String shareCode) {
        Trip trip = tripRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));
        recordProposalView(trip);
        return toPublicDto(trip);
    }

    /**
     * Contabiliza abertura da proposta pública (badge de atividade no pipeline).
     * Throttle de 45s evita contagem dupla por remount/prefetch do front.
     */
    private void recordProposalView(Trip trip) {
        Instant now = Instant.now();
        Instant last = trip.getProposalLastViewedAt();
        if (last != null && Duration.between(last, now).getSeconds() < 45) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (trip.getProposalViewsDay() == null || !trip.getProposalViewsDay().equals(today)) {
            trip.setProposalViewsDay(today);
            trip.setProposalViewsToday(1);
        } else {
            int todayCount = trip.getProposalViewsToday() == null ? 0 : trip.getProposalViewsToday();
            trip.setProposalViewsToday(todayCount + 1);
        }
        int total = trip.getProposalViewCount() == null ? 0 : trip.getProposalViewCount();
        trip.setProposalViewCount(total + 1);
        trip.setProposalLastViewedAt(now);
        agencyOpportunityService.recordActivityForTrip(
                trip.id,
                OpportunityActivityType.PROPOSAL_VIEWED,
                "Proposta visualizada pelo cliente",
                null);
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

        ProposalStatus next = ProposalStatus.CONFIRMED;
        if (trip.getFinalPrice() != null && trip.getFinalPrice().compareTo(BigDecimal.ZERO) > 0) {
            next = ProposalStatus.PENDING_PAYMENT;
        } else if (trip.getOperationStatus() == null) {
            trip.setOperationStatus(OperationStatus.TO_RESERVE);
        }

        trip.setProposalStatus(next);
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);
        agencyOpportunityService.syncStageFromProposalStatus(trip.id, next);
        agencyOpportunityService.recordActivityForTrip(
                trip.id,
                OpportunityActivityType.APPROVED,
                "Proposta aprovada pelo cliente",
                next == ProposalStatus.PENDING_PAYMENT
                        ? "Aguardando pagamento"
                        : "Confirmada");
        taskAutomationService.onProposalApprovedForTrip(trip.id);

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
        agencyOpportunityService.syncStageFromProposalStatus(trip.id, ProposalStatus.REJECTED, reason);

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
                || status == ProposalStatus.CANCELLED
                || status == ProposalStatus.COMPLETED
                || status == ProposalStatus.CONFIRMED
                || status == ProposalStatus.IN_TRIP) {
            throw new BadRequestException("Proposta já está em status final: " + status);
        }
        if (status == ProposalStatus.APPROVED || status == ProposalStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Proposta já foi aprovada");
        }
        if (status != ProposalStatus.SENT
                && status != ProposalStatus.DRAFT
                && status != ProposalStatus.NEGOTIATING) {
            throw new BadRequestException("Proposta não está disponível para esta ação (status: " + status + ")");
        }
    }

    /** Preço travado após o cliente aceitar (pagamento / confirmada / viagem). */
    public static boolean isPricingLocked(ProposalStatus status) {
        if (status == null) {
            return false;
        }
        return status == ProposalStatus.PENDING_PAYMENT
                || status == ProposalStatus.APPROVED
                || status == ProposalStatus.CONFIRMED
                || status == ProposalStatus.IN_TRIP
                || status == ProposalStatus.COMPLETED;
    }

    private void assertPricingEditable(Trip trip) {
        ProposalStatus status = trip.getProposalStatus() != null ? trip.getProposalStatus() : ProposalStatus.DRAFT;
        if (isPricingLocked(status)) {
            throw new BadRequestException(
                    "O valor do pacote não pode ser alterado após a proposta ser aceita (status: " + status + ")");
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
        assertPricingEditable(trip);

        BigDecimal base;
        if (request.getBaseCostItems() != null) {
            List<BaseCostItemDTO> items = normalizeBaseCostItems(request.getBaseCostItems());
            base = items.stream()
                    .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            trip.setBaseCostItems(items);
        } else if (request.getBaseCost() != null) {
            base = request.getBaseCost();
        } else {
            throw new BadRequestException("baseCost is required");
        }

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

    private List<BaseCostItemDTO> normalizeBaseCostItems(List<BaseCostItemDTO> raw) {
        if (raw == null) {
            return List.of();
        }
        List<BaseCostItemDTO> out = new ArrayList<>();
        for (BaseCostItemDTO item : raw) {
            if (item == null) continue;
            String label = item.getLabel() != null ? item.getLabel().trim() : "";
            if (label.isEmpty()) {
                label = "Item";
            }
            BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                amount = BigDecimal.ZERO;
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);
            String code = item.getCode() != null ? item.getCode().trim().toUpperCase() : "CUSTOM";
            if (!List.of("FLIGHT", "HOTEL", "INSURANCE", "TOURS", "CUSTOM", "OTHER").contains(code)) {
                code = "CUSTOM";
            }
            String id = item.getId() != null && !item.getId().isBlank()
                    ? item.getId().trim()
                    : UUID.randomUUID().toString();
            out.add(BaseCostItemDTO.builder()
                    .id(id)
                    .code(code)
                    .label(label)
                    .amount(amount)
                    .build());
        }
        return out;
    }

    public List<ProposalTierDTO> listTiers(UUID tripId, UUID userId) {
        requireAgencyTripAccess(tripId, userId);
        return tierRepository.findByTripId(tripId).stream()
                .map(this::toTierDto)
                .toList();
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
        if (request != null && request.getAllowNegotiation() != null) {
            trip.setAllowNegotiation(Boolean.TRUE.equals(request.getAllowNegotiation()));
        }
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
                "{\"proposalStatus\":\"SENT\",\"emailQueued\":" + queued
                        + ",\"allowNegotiation\":" + trip.isAllowNegotiation() + "}",
                "Proposta enviada para " + clientEmail, null);

        agencyOpportunityService.recordActivityForTrip(
                trip.id,
                OpportunityActivityType.PROPOSAL_SENT,
                "Proposta enviada ao cliente",
                clientEmail);

        taskAutomationService.onProposalSentForTrip(trip.id, trip.getProposalExpiresAt());

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
    public Trip updateProposalStatus(UUID tripId, UUID userId, UpdateProposalStatusRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        if (request == null) {
            throw new BadRequestException("request is required");
        }

        ProposalStatus status = request.getProposalStatus();
        boolean changed = false;

        if (request.getAllowNegotiation() != null) {
            trip.setAllowNegotiation(request.getAllowNegotiation());
            changed = true;
        }

        if (request.getOperationStatus() != null) {
            trip.setOperationStatus(request.getOperationStatus());
            changed = true;
        }

        if (status != null) {
            if (status == ProposalStatus.NEGOTIATING && !trip.isAllowNegotiation()) {
                throw new BadRequestException(
                        "Negociação não está habilitada nesta proposta. Reenvie com a opção de negociar.");
            }
            if (status == ProposalStatus.APPROVED) {
                status = ProposalStatus.CONFIRMED;
            }
            if (status == ProposalStatus.CONFIRMED && trip.getOperationStatus() == null) {
                trip.setOperationStatus(OperationStatus.TO_RESERVE);
            }
            if (status == ProposalStatus.CANCELLED && trip.getOperationStatus() != null) {
                trip.setOperationStatus(OperationStatus.CANCELLED);
            }
            trip.setProposalStatus(status);
            changed = true;
        }

        if (!changed) {
            throw new BadRequestException("proposalStatus, operationStatus or allowNegotiation is required");
        }

        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);

        ProposalStatus effective = trip.getProposalStatus();
        if (status != null) {
            agencyOpportunityService.syncStageFromProposalStatus(trip.id, effective);
        }

        B2bTripLogAction action = switch (effective) {
            case APPROVED, PENDING_PAYMENT -> B2bTripLogAction.PROPOSAL_APPROVED;
            case CONFIRMED, IN_TRIP, COMPLETED -> B2bTripLogAction.PROPOSAL_CONFIRMED;
            case REJECTED, LOST, CANCELLED -> B2bTripLogAction.PROPOSAL_REJECTED;
            case SENT, NEGOTIATING -> B2bTripLogAction.PROPOSAL_SENT;
            default -> B2bTripLogAction.TRIP_STATUS_CHANGED;
        };
        auditService.record(trip, userId, action, "TRIP", trip.id, null,
                "{\"proposalStatus\":\"" + effective + "\""
                        + (trip.getOperationStatus() != null
                        ? ",\"operationStatus\":\"" + trip.getOperationStatus() + "\"" : "")
                        + ",\"allowNegotiation\":" + trip.isAllowNegotiation() + "}",
                "Status da proposta alterado para " + effective, null);
        return trip;
    }

    /** Compatível com callers que só passam o status. */
    @Transactional
    public Trip updateProposalStatus(UUID tripId, UUID userId, ProposalStatus status) {
        return updateProposalStatus(tripId, userId, UpdateProposalStatusRequest.builder()
                .proposalStatus(status)
                .build());
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

    @Transactional
    public Trip updateFollowUp(UUID tripId, UUID userId, UpdateProposalFollowUpRequest request) {
        Trip trip = requireAgencyTripAccess(tripId, userId);
        Instant at = request != null ? request.getNextFollowUpAt() : null;
        trip.setNextFollowUpAt(at);
        tripRepository.persist(trip);
        return trip;
    }

    public PipelinePageDTO listPipeline(
            UUID userId,
            ProposalStatus status,
            UUID consultantId,
            String q,
            int page,
            int size) {
        return listPipeline(userId, status, consultantId, q, PipelineScope.ACTIVE, page, size);
    }

    public PipelinePageDTO listPipeline(
            UUID userId,
            ProposalStatus status,
            UUID consultantId,
            String q,
            PipelineScope scope,
            int page,
            int size) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        Agency agency = member.getAgency();
        UUID scopeUserId = member.getAgencyRole() == AgencyRole.AGENCY_OWNER ? null : userId;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PipelineScope resolvedScope = scope != null ? scope : PipelineScope.ACTIVE;

        List<Trip> trips = tripRepository.findPipeline(
                agency.id, status, consultantId, q, scopeUserId, resolvedScope, safePage, safeSize);
        for (Trip trip : trips) {
            syncPipelineLifecycle(trip);
        }
        long total = tripRepository.countPipeline(
                agency.id, status, consultantId, q, scopeUserId, resolvedScope);

        return PipelinePageDTO.builder()
                .items(trips.stream()
                        .filter(t -> matchesScopeAfterSync(t, status, resolvedScope))
                        .map(this::toPipelineCard)
                        .toList())
                .total(total)
                .page(safePage)
                .size(safeSize)
                .build();
    }

    /** Compat: lista plana sem filtros (analytics e callers legados). */
    public List<PipelineTripCardDTO> listPipeline(UUID userId) {
        return listPipeline(userId, null, null, null, PipelineScope.ALL, 0, 100).getItems();
    }

    private boolean matchesScopeAfterSync(Trip trip, ProposalStatus statusFilter, PipelineScope scope) {
        ProposalStatus s = trip.getProposalStatus() != null ? trip.getProposalStatus() : ProposalStatus.DRAFT;
        if (statusFilter != null) {
            return s == statusFilter;
        }
        if (scope == PipelineScope.ACTIVE) {
            return s.isActivePipeline();
        }
        if (scope == PipelineScope.ARCHIVE) {
            return s.isArchive();
        }
        return true;
    }

    /**
     * Promove Confirmada → Em viagem → Concluída com base nas datas da viagem.
     */
    private void syncPipelineLifecycle(Trip trip) {
        ProposalStatus status = trip.getProposalStatus();
        if (status == null || status.isArchive()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate();

        if (end != null && today.isAfter(end)
                && (status == ProposalStatus.CONFIRMED
                || status == ProposalStatus.IN_TRIP
                || status == ProposalStatus.APPROVED)) {
            trip.setProposalStatus(ProposalStatus.COMPLETED);
            tripRepository.persist(trip);
            return;
        }

        if (start != null && end != null
                && !today.isBefore(start) && !today.isAfter(end)
                && (status == ProposalStatus.CONFIRMED || status == ProposalStatus.APPROVED)) {
            trip.setProposalStatus(ProposalStatus.IN_TRIP);
            if (trip.getOperationStatus() == null) {
                trip.setOperationStatus(OperationStatus.TO_RESERVE);
            }
            tripRepository.persist(trip);
        }
    }

    /** Compat: analytics sem filtro de período (todo o histórico). */
    @Transactional
    public AgencyAnalyticsDTO analytics(UUID userId) {
        return analytics(userId, "ALL");
    }

    /**
     * BI da agência com recorte temporal.
     *
     * @param period ALL | MONTH | QUARTER | YEAR
     */
    @Transactional
    public AgencyAnalyticsDTO analytics(UUID userId, String period) {
        AgencyMember member = agencyService.requireMembershipOrThrow(userId);
        UUID agencyId = member.getAgency().id;
        String normalizedPeriod = normalizeAnalyticsPeriod(period);

        PeriodWindow current = resolvePeriodWindow(normalizedPeriod, false);
        PeriodWindow previous = resolvePeriodWindow(normalizedPeriod, true);

        List<Trip> allTrips = tripRepository.findByAgencyId(agencyId);
        List<Trip> currentTrips = filterTripsByCreatedAt(allTrips, current);
        List<Trip> previousTrips = "ALL".equals(normalizedPeriod)
                ? List.of()
                : filterTripsByCreatedAt(allTrips, previous);

        Metrics currentMetrics = computeMetrics(currentTrips);
        Metrics previousMetrics = computeMetrics(previousTrips);

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

        List<AgencyMember> team = agencyMemberRepository.findAllByAgency(agencyId);
        boolean showLeaderboard = member.getAgencyRole() == AgencyRole.AGENCY_OWNER && team.size() > 1;
        List<AgencyAnalyticsDTO.ConsultantStat> leaderboard = showLeaderboard
                ? buildTeamLeaderboard(currentTrips)
                : List.of();

        return AgencyAnalyticsDTO.builder()
                .proposalsDraft(currentMetrics.draft)
                .proposalsSent(currentMetrics.sent)
                .proposalsApproved(currentMetrics.approved + currentMetrics.pendingPayment + currentMetrics.confirmed)
                .proposalsRejected(currentMetrics.rejected)
                .proposalsLost(currentMetrics.lost)
                .conversionRate(currentMetrics.conversionRate)
                .forecastRevenue(currentMetrics.volume)
                .grossVolume(currentMetrics.volume)
                .estimatedMargin(currentMetrics.margin)
                .avgMarginPercentage(currentMetrics.avgMarginPct)
                .activeClients(agencyClients.size())
                .memberClients(memberClients)
                .guestClients(guestClients)
                .avgPackagePrice(currentMetrics.avgPackage)
                .topDestinations(currentMetrics.topByCount)
                .period(normalizedPeriod)
                .grossVolumeDeltaPct(pctChange(previousMetrics.volume, currentMetrics.volume))
                .estimatedMarginDeltaPct(pctChange(previousMetrics.margin, currentMetrics.margin))
                .conversionRateDeltaPts(round1(currentMetrics.conversionRate - previousMetrics.conversionRate))
                .previousGrossVolume(previousMetrics.volume)
                .previousEstimatedMargin(previousMetrics.margin)
                .previousConversionRate(previousMetrics.conversionRate)
                .previousAvgMarginPercentage(previousMetrics.avgMarginPct)
                .destinationsByMargin(currentMetrics.topByMargin)
                .showTeamLeaderboard(showLeaderboard)
                .teamLeaderboard(leaderboard)
                .build();
    }

    private static String normalizeAnalyticsPeriod(String period) {
        if (period == null || period.isBlank()) {
            return "ALL";
        }
        return switch (period.trim().toUpperCase(Locale.ROOT)) {
            case "MONTH", "QUARTER", "YEAR", "ALL" -> period.trim().toUpperCase(Locale.ROOT);
            default -> "ALL";
        };
    }

    private record PeriodWindow(Instant start, Instant end) {}

    private PeriodWindow resolvePeriodWindow(String period, boolean previous) {
        java.time.ZoneId zone = java.time.ZoneId.of("America/Sao_Paulo");
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone);
        java.time.ZonedDateTime start;
        java.time.ZonedDateTime end;

        switch (period) {
            case "MONTH" -> {
                java.time.ZonedDateTime cursor = previous ? now.minusMonths(1) : now;
                start = cursor.withDayOfMonth(1).toLocalDate().atStartOfDay(zone);
                end = previous
                        ? start.plusMonths(1)
                        : now;
            }
            case "QUARTER" -> {
                int month = now.getMonthValue();
                int quarterStartMonth = ((month - 1) / 3) * 3 + 1;
                java.time.ZonedDateTime qStart = now
                        .withMonth(quarterStartMonth)
                        .withDayOfMonth(1)
                        .toLocalDate()
                        .atStartOfDay(zone);
                if (previous) {
                    start = qStart.minusMonths(3);
                    end = qStart;
                } else {
                    start = qStart;
                    end = now;
                }
            }
            case "YEAR" -> {
                java.time.ZonedDateTime yStart = now.withDayOfYear(1).toLocalDate().atStartOfDay(zone);
                if (previous) {
                    start = yStart.minusYears(1);
                    end = yStart;
                } else {
                    start = yStart;
                    end = now;
                }
            }
            default -> {
                // ALL — janela "atual" = tudo; "anterior" vazia
                start = java.time.Instant.EPOCH.atZone(zone);
                end = previous ? java.time.Instant.EPOCH.atZone(zone) : now;
            }
        }
        return new PeriodWindow(start.toInstant(), end.toInstant());
    }

    private static List<Trip> filterTripsByCreatedAt(List<Trip> trips, PeriodWindow window) {
        if (window == null) {
            return trips;
        }
        // Janela vazia (ex.: previous de ALL)
        if (!window.start().isBefore(window.end())) {
            return List.of();
        }
        List<Trip> out = new ArrayList<>();
        for (Trip t : trips) {
            Instant created = t.getCreatedAt() != null ? t.getCreatedAt() : t.getUpdatedAt();
            if (created == null) {
                continue;
            }
            if (!created.isBefore(window.start()) && created.isBefore(window.end())) {
                out.add(t);
            }
        }
        return out;
    }

    private static final class Metrics {
        long draft, sent, approved, rejected, lost, pendingPayment, confirmed;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal margin = BigDecimal.ZERO;
        BigDecimal avgPackage = BigDecimal.ZERO;
        Double avgMarginPct;
        double conversionRate;
        List<AgencyAnalyticsDTO.DestinationStat> topByCount = List.of();
        List<AgencyAnalyticsDTO.DestinationStat> topByMargin = List.of();
    }

    private static final class DestAgg {
        long count;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal margin = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
    }

    private Metrics computeMetrics(List<Trip> trips) {
        Metrics m = new Metrics();
        BigDecimal baseSumForMarkup = BigDecimal.ZERO;
        BigDecimal marginSumForMarkup = BigDecimal.ZERO;
        long pricedVolumeTrips = 0;
        Map<String, DestAgg> destinations = new LinkedHashMap<>();

        for (Trip t : trips) {
            ProposalStatus s = t.getProposalStatus() != null ? t.getProposalStatus() : ProposalStatus.DRAFT;
            boolean countsAsVolume = t.getFinalPrice() != null
                    && (s == ProposalStatus.DRAFT
                    || s == ProposalStatus.QUOTING
                    || s == ProposalStatus.SENT
                    || s == ProposalStatus.NEGOTIATING
                    || s == ProposalStatus.APPROVED
                    || s == ProposalStatus.PENDING_PAYMENT
                    || s == ProposalStatus.CONFIRMED
                    || s == ProposalStatus.IN_TRIP
                    || s == ProposalStatus.COMPLETED);
            switch (s) {
                case DRAFT, QUOTING -> m.draft++;
                case SENT, NEGOTIATING -> m.sent++;
                case APPROVED -> m.approved++;
                case PENDING_PAYMENT -> m.pendingPayment++;
                case CONFIRMED, IN_TRIP, COMPLETED -> m.confirmed++;
                case REJECTED, CANCELLED -> m.rejected++;
                case LOST -> m.lost++;
            }

            String dest = resolveDestination(t);
            DestAgg agg = destinations.computeIfAbsent(dest, k -> new DestAgg());
            agg.count++;

            if (countsAsVolume) {
                m.volume = m.volume.add(t.getFinalPrice());
                pricedVolumeTrips++;
                agg.volume = agg.volume.add(t.getFinalPrice());
                if (t.getBaseCost() != null) {
                    BigDecimal tripMargin = t.getFinalPrice().subtract(t.getBaseCost());
                    m.margin = m.margin.add(tripMargin);
                    agg.margin = agg.margin.add(tripMargin);
                    if (t.getBaseCost().compareTo(BigDecimal.ZERO) > 0) {
                        baseSumForMarkup = baseSumForMarkup.add(t.getBaseCost());
                        marginSumForMarkup = marginSumForMarkup.add(tripMargin);
                        agg.base = agg.base.add(t.getBaseCost());
                    }
                }
            }
        }

        long conversionDenom = m.sent + m.approved + m.pendingPayment + m.confirmed + m.rejected + m.lost;
        m.conversionRate = conversionDenom == 0 ? 0.0
                : Math.round(((m.approved + m.pendingPayment + m.confirmed) * 10000.0) / conversionDenom) / 100.0;

        m.avgPackage = pricedVolumeTrips == 0
                ? BigDecimal.ZERO
                : m.volume.divide(BigDecimal.valueOf(pricedVolumeTrips), 2, RoundingMode.HALF_UP);

        if (baseSumForMarkup.compareTo(BigDecimal.ZERO) > 0) {
            m.avgMarginPct = marginSumForMarkup
                    .multiply(BigDecimal.valueOf(100))
                    .divide(baseSumForMarkup, 1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        m.topByCount = destinations.entrySet().stream()
                .sorted(Map.Entry.<String, DestAgg>comparingByValue(
                        (a, b) -> Long.compare(b.count, a.count)))
                .limit(10)
                .map(e -> toDestinationStat(e.getKey(), e.getValue()))
                .toList();

        m.topByMargin = destinations.entrySet().stream()
                .filter(e -> e.getValue().base.compareTo(BigDecimal.ZERO) > 0
                        || e.getValue().margin.compareTo(BigDecimal.ZERO) != 0)
                .sorted((a, b) -> {
                    double pa = marginPct(a.getValue());
                    double pb = marginPct(b.getValue());
                    int cmp = Double.compare(pb, pa);
                    if (cmp != 0) return cmp;
                    return b.getValue().margin.compareTo(a.getValue().margin);
                })
                .limit(10)
                .map(e -> toDestinationStat(e.getKey(), e.getValue()))
                .toList();

        return m;
    }

    private static String resolveDestination(Trip t) {
        String dest = t.getName() != null ? t.getName() : "—";
        if (t.getSegments() != null && !t.getSegments().isEmpty()
                && t.getSegments().get(0).getCityId() != null) {
            dest = t.getSegments().get(0).getCityId();
        }
        return dest;
    }

    private static double marginPct(DestAgg agg) {
        if (agg.base.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return agg.margin.multiply(BigDecimal.valueOf(100))
                .divide(agg.base, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static AgencyAnalyticsDTO.DestinationStat toDestinationStat(String name, DestAgg agg) {
        Double pct = agg.base.compareTo(BigDecimal.ZERO) > 0 ? marginPct(agg) : null;
        return AgencyAnalyticsDTO.DestinationStat.builder()
                .cityOrName(name)
                .count(agg.count)
                .volume(agg.volume)
                .margin(agg.margin)
                .marginPercentage(pct)
                .build();
    }

    private static final class ConsultantAgg {
        UUID id;
        String name;
        long handled;
        long won;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal margin = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
    }

    private List<AgencyAnalyticsDTO.ConsultantStat> buildTeamLeaderboard(List<Trip> trips) {
        Map<UUID, ConsultantAgg> byConsultant = new LinkedHashMap<>();

        for (Trip t : trips) {
            User consultant = t.getAssignedConsultant() != null
                    ? t.getAssignedConsultant()
                    : t.getCreatedBy();
            if (consultant == null || consultant.id == null) {
                continue;
            }
            ConsultantAgg agg = byConsultant.computeIfAbsent(consultant.id, id -> {
                ConsultantAgg c = new ConsultantAgg();
                c.id = id;
                c.name = consultant.getFullName() != null && !consultant.getFullName().isBlank()
                        ? consultant.getFullName()
                        : (consultant.getEmail() != null ? consultant.getEmail() : "Consultor");
                return c;
            });

            ProposalStatus s = t.getProposalStatus() != null ? t.getProposalStatus() : ProposalStatus.DRAFT;
            boolean inFunnel = s != ProposalStatus.DRAFT && s != ProposalStatus.QUOTING;
            if (inFunnel) {
                agg.handled++;
            }
            if (s == ProposalStatus.APPROVED
                    || s == ProposalStatus.PENDING_PAYMENT
                    || s == ProposalStatus.CONFIRMED
                    || s == ProposalStatus.IN_TRIP
                    || s == ProposalStatus.COMPLETED) {
                agg.won++;
            }

            boolean countsAsVolume = t.getFinalPrice() != null
                    && (s == ProposalStatus.DRAFT
                    || s == ProposalStatus.QUOTING
                    || s == ProposalStatus.SENT
                    || s == ProposalStatus.NEGOTIATING
                    || s == ProposalStatus.APPROVED
                    || s == ProposalStatus.PENDING_PAYMENT
                    || s == ProposalStatus.CONFIRMED
                    || s == ProposalStatus.IN_TRIP
                    || s == ProposalStatus.COMPLETED);
            if (countsAsVolume) {
                agg.volume = agg.volume.add(t.getFinalPrice());
                if (t.getBaseCost() != null) {
                    BigDecimal tripMargin = t.getFinalPrice().subtract(t.getBaseCost());
                    agg.margin = agg.margin.add(tripMargin);
                    if (t.getBaseCost().compareTo(BigDecimal.ZERO) > 0) {
                        agg.base = agg.base.add(t.getBaseCost());
                    }
                }
            }
        }

        return byConsultant.values().stream()
                .sorted((a, b) -> {
                    int cmp = b.margin.compareTo(a.margin);
                    if (cmp != 0) return cmp;
                    double ca = a.handled == 0 ? 0 : (a.won * 100.0) / a.handled;
                    double cb = b.handled == 0 ? 0 : (b.won * 100.0) / b.handled;
                    return Double.compare(cb, ca);
                })
                .map(agg -> {
                    double conv = agg.handled == 0 ? 0.0
                            : Math.round((agg.won * 10000.0) / agg.handled) / 100.0;
                    Double marginPct = agg.base.compareTo(BigDecimal.ZERO) > 0
                            ? agg.margin.multiply(BigDecimal.valueOf(100))
                                    .divide(agg.base, 1, RoundingMode.HALF_UP)
                                    .doubleValue()
                            : null;
                    return AgencyAnalyticsDTO.ConsultantStat.builder()
                            .consultantId(agg.id)
                            .consultantName(agg.name)
                            .proposalsHandled(agg.handled)
                            .proposalsWon(agg.won)
                            .conversionRate(conv)
                            .volume(agg.volume)
                            .margin(agg.margin)
                            .marginPercentage(marginPct)
                            .build();
                })
                .toList();
    }

    private static Double pctChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) {
            return null;
        }
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
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
                        && trip.getProposalStatus() != ProposalStatus.CONFIRMED
                        && trip.getProposalStatus() != ProposalStatus.IN_TRIP
                        && trip.getProposalStatus() != ProposalStatus.COMPLETED)
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
                .allowNegotiation(t.isAllowNegotiation())
                .operationStatus(t.getOperationStatus())
                .paymentBadge(resolvePaymentBadge(t))
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
                .clientPhone(t.getClient() != null ? t.getClient().getPhone() : null)
                .proposalLastViewedAt(t.getProposalLastViewedAt())
                .proposalViewCount(t.getProposalViewCount())
                .proposalViewsToday(t.getProposalViewsToday())
                .build();
    }

    private String resolvePaymentBadge(Trip t) {
        ProposalStatus s = t.getProposalStatus() != null ? t.getProposalStatus() : ProposalStatus.DRAFT;
        boolean hasPrice = t.getFinalPrice() != null && t.getFinalPrice().compareTo(BigDecimal.ZERO) > 0;
        return switch (s) {
            case PENDING_PAYMENT -> "PENDING";
            case CONFIRMED, IN_TRIP, COMPLETED -> hasPrice ? "PAID" : "NONE";
            case APPROVED -> "NONE";
            case CANCELLED -> hasPrice ? "REFUNDED" : "NONE";
            default -> hasPrice ? "NOT_REQUESTED" : "NONE";
        };
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
