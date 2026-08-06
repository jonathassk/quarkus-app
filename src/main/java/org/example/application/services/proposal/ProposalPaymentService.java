package org.example.application.services.proposal;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.proposal.ProposalCheckoutRequest;
import org.example.application.dto.proposal.ProposalCheckoutResponse;
import org.example.application.services.B2bAuditService;
import org.example.application.services.agency.AgencyOpportunityService;
import org.example.application.services.notification.NotificationService;
import org.example.domain.entity.AgencyMember;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripPayment;
import org.example.domain.enums.B2bTripLogAction;
import org.example.domain.enums.NotificationKind;
import org.example.domain.enums.ProposalStatus;
import org.example.domain.enums.TripPaymentKind;
import org.example.domain.enums.TripPaymentStatus;
import org.example.domain.repository.AgencyMemberRepository;
import org.example.domain.repository.TripPaymentRepository;
import org.example.domain.repository.TripRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Checkout Stripe amarrado à proposta pública (sinal / valor cheio).
 */
@Slf4j
@ApplicationScoped
public class ProposalPaymentService {

    /** Fração do finalPrice cobrada como sinal. */
    public static final BigDecimal DEFAULT_DEPOSIT_RATIO = new BigDecimal("0.30");

    public static final String PAYMENT_TYPE_PROPOSAL = "PROPOSAL";

    @Inject
    TripRepository tripRepository;
    @Inject
    TripPaymentRepository tripPaymentRepository;
    @Inject
    AgencyMemberRepository agencyMemberRepository;
    @Inject
    AgencyOpportunityService agencyOpportunityService;
    @Inject
    B2bAuditService auditService;
    @Inject
    NotificationService notificationService;
    @Inject
    org.example.application.services.ops.OperationalWorkspaceService operationalWorkspaceService;

    @ConfigProperty(name = "stripe.api.key")
    Optional<String> apiKey;

    @ConfigProperty(name = "stripe.success.url")
    String successUrl;

    @ConfigProperty(name = "stripe.cancel.url")
    String cancelUrl;

    @ConfigProperty(name = "quarkus.http.cors.origins", defaultValue = "http://localhost:3000")
    String corsOriginsConfig;

    @ConfigProperty(name = "app.public-url", defaultValue = "http://localhost:3000")
    String appPublicUrl;

    @Transactional
    public ProposalCheckoutResponse startCheckout(String shareCode, ProposalCheckoutRequest request) {
        return startCheckout(shareCode, request, null);
    }

    @Transactional
    public ProposalCheckoutResponse startCheckout(
            String shareCode, ProposalCheckoutRequest request, String idempotencyKeyHeader) {
        if (!isStripeConfigured()) {
            throw new ServiceUnavailableException("Payment service is not configured");
        }
        Trip trip = tripRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new NotFoundException("Proposal not found"));

        ProposalStatus status = trip.getProposalStatus() != null ? trip.getProposalStatus() : ProposalStatus.DRAFT;
        if (status != ProposalStatus.PENDING_PAYMENT && status != ProposalStatus.APPROVED) {
            throw new BadRequestException(
                    "Checkout só é permitido após a aprovação da proposta (status atual: " + status + ")");
        }
        if (ProposalService.isExpired(trip)) {
            throw new BadRequestException("Esta proposta expirou e não aceita mais pagamento");
        }

        BigDecimal finalPrice = trip.getFinalPrice();
        if (finalPrice == null || finalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Proposta sem valor definido — não há o que cobrar");
        }

        TripPaymentKind kind = resolveKind(request, trip);
        BigDecimal amount = resolveAmount(trip, kind);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Valor a cobrar é zero");
        }

        String currency = trip.getCurrency() != null && !trip.getCurrency().isBlank()
                ? trip.getCurrency().trim().toUpperCase()
                : "BRL";

        // Reusa sessão Stripe aberta (evita cobranças duplicadas em double-submit).
        Optional<TripPayment> openPending =
                tripPaymentRepository.findLatestPendingWithSession(trip.id, kind);
        if (openPending.isPresent()) {
            TripPayment existing = openPending.get();
            try {
                Stripe.apiKey = apiKey.orElse("").trim();
                Session existingSession = Session.retrieve(existing.getStripeSessionId());
                if ("open".equalsIgnoreCase(existingSession.getStatus())
                        && existingSession.getUrl() != null
                        && !existingSession.getUrl().isBlank()) {
                    log.info(
                            "Reusing open proposal checkout tripId={} paymentId={} session={}",
                            trip.id,
                            existing.id,
                            existing.getStripeSessionId());
                    return ProposalCheckoutResponse.builder()
                            .checkoutUrl(existingSession.getUrl())
                            .paymentId(existing.id)
                            .kind(kind)
                            .amount(existing.getAmount() != null ? existing.getAmount() : amount)
                            .currency(currency)
                            .build();
                }
            } catch (Exception e) {
                log.warn(
                        "Could not reuse pending checkout paymentId={} session={}: {}",
                        existing.id,
                        existing.getStripeSessionId(),
                        e.getMessage());
            }
        }

        TripPayment payment = TripPayment.builder()
                .trip(trip)
                .kind(kind)
                .amount(amount)
                .currency(currency)
                .status(TripPaymentStatus.PENDING)
                .build();
        tripPaymentRepository.persist(payment);

        if (status != ProposalStatus.PENDING_PAYMENT) {
            trip.setProposalStatus(ProposalStatus.PENDING_PAYMENT);
            trip.setLastContactAt(Instant.now());
            tripRepository.persist(trip);
            auditService.recordExternalActor(
                    trip,
                    "Cliente (link público)",
                    B2bTripLogAction.PROPOSAL_PAYMENT_PENDING,
                    "TRIP",
                    trip.id,
                    null,
                    "{\"proposalStatus\":\"PENDING_PAYMENT\",\"paymentKind\":\"" + kind + "\"}",
                    "Checkout iniciado — aguardando pagamento",
                    null);
        }

        try {
            Stripe.apiKey = apiKey.orElse("").trim();
            String resolvedSuccess = resolveRedirectUrl(
                    request != null ? request.getSuccessUrl() : null,
                    defaultProposalSuccessUrl(shareCode));
            String resolvedCancel = resolveRedirectUrl(
                    request != null ? request.getCancelUrl() : null,
                    defaultProposalCancelUrl(shareCode));

            long unitAmount = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            String productName = kind == TripPaymentKind.DEPOSIT
                    ? "Sinal — " + trip.getName()
                    : "Pagamento — " + trip.getName();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(withSessionIdParam(resolvedSuccess))
                    .setCancelUrl(resolvedCancel)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency.toLowerCase())
                                    .setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(productName)
                                            .build())
                                    .build())
                            .build())
                    .putMetadata("paymentType", PAYMENT_TYPE_PROPOSAL)
                    .putMetadata("targetId", trip.id.toString())
                    .putMetadata("tripPaymentId", payment.id.toString())
                    .putMetadata("paymentKind", kind.name())
                    .build();

            String idempotencyKey = resolveIdempotencyKey(
                    idempotencyKeyHeader,
                    "proposal-checkout:" + shareCode + ":" + kind.name());
            RequestOptions requestOptions =
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
            Session session = Session.create(params, requestOptions);

            Optional<TripPayment> alreadyLinked =
                    tripPaymentRepository.findByStripeSessionId(session.getId());
            if (alreadyLinked.isPresent() && !alreadyLinked.get().id.equals(payment.id)) {
                payment.setStatus(TripPaymentStatus.FAILED);
                tripPaymentRepository.persist(payment);
                TripPayment linked = alreadyLinked.get();
                log.info(
                        "Idempotent proposal checkout reused tripId={} paymentId={} session={}",
                        trip.id,
                        linked.id,
                        session.getId());
                return ProposalCheckoutResponse.builder()
                        .checkoutUrl(session.getUrl())
                        .paymentId(linked.id)
                        .kind(kind)
                        .amount(linked.getAmount() != null ? linked.getAmount() : amount)
                        .currency(currency)
                        .build();
            }

            payment.setStripeSessionId(session.getId());
            tripPaymentRepository.persist(payment);

            log.info("Proposal checkout started tripId={} paymentId={} kind={} amount={} session={}",
                    trip.id, payment.id, kind, amount, session.getId());

            return ProposalCheckoutResponse.builder()
                    .checkoutUrl(session.getUrl())
                    .paymentId(payment.id)
                    .kind(kind)
                    .amount(amount)
                    .currency(currency)
                    .build();
        } catch (BadRequestException | ServiceUnavailableException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create proposal checkout shareCode={}", shareCode, e);
            payment.setStatus(TripPaymentStatus.FAILED);
            tripPaymentRepository.persist(payment);
            throw new BadRequestException("Error starting payment: " + e.getMessage());
        }
    }

    @Transactional
    public void fulfillProposalPayment(UUID tripPaymentId, String stripeSessionId,
                                       BigDecimal amountPaid, String currency) {
        TripPayment payment = tripPaymentRepository.findById(tripPaymentId);
        if (payment == null) {
            log.warn("TripPayment {} not found — proposal fulfillment skipped", tripPaymentId);
            return;
        }
        if (payment.getStatus() == TripPaymentStatus.PAID) {
            log.info("TripPayment {} already PAID — skipping", tripPaymentId);
            return;
        }

        payment.setStatus(TripPaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        if (stripeSessionId != null) {
            payment.setStripeSessionId(stripeSessionId);
        }
        if (amountPaid != null) {
            payment.setAmount(amountPaid);
        }
        if (currency != null && !currency.isBlank()) {
            payment.setCurrency(currency.trim().toUpperCase());
        }
        tripPaymentRepository.persist(payment);

        Trip trip = payment.getTrip();
        if (trip == null) {
            return;
        }
        trip.setProposalStatus(ProposalStatus.CONFIRMED);
        if (trip.getOperationStatus() == null) {
            trip.setOperationStatus(org.example.domain.enums.OperationStatus.PREPARING_RESERVATIONS);
        }
        trip.setLastContactAt(Instant.now());
        tripRepository.persist(trip);
        agencyOpportunityService.syncStageFromProposalStatus(trip.id, ProposalStatus.CONFIRMED);
        operationalWorkspaceService.materializeFromApprovedProposal(trip);

        auditService.recordExternalActor(
                trip,
                "Cliente (pagamento Stripe)",
                B2bTripLogAction.PROPOSAL_PAYMENT_RECEIVED,
                "PAYMENT",
                payment.id,
                null,
                "{\"kind\":\"" + payment.getKind() + "\",\"amount\":" + payment.getAmount() + "}",
                "Pagamento da proposta confirmado",
                null);
        auditService.recordExternalActor(
                trip,
                "Cliente (pagamento Stripe)",
                B2bTripLogAction.PROPOSAL_CONFIRMED,
                "TRIP",
                trip.id,
                null,
                "{\"proposalStatus\":\"CONFIRMED\"}",
                "Proposta confirmada após pagamento",
                null);

        notifyAgencyOfPaymentConfirmed(trip, payment);
        log.info("Proposal payment fulfilled tripId={} paymentId={} → CONFIRMED", trip.id, payment.id);
    }

    private void notifyAgencyOfPaymentConfirmed(Trip trip, TripPayment payment) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
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
        if (ids.isEmpty()) {
            return;
        }
        String tripName = trip.getName() != null ? trip.getName() : "proposta";
        String amountLabel =
                payment.getAmount() != null
                        ? payment.getAmount() + " " + (payment.getCurrency() != null ? payment.getCurrency() : "")
                        : "";
        notificationService.createForUsers(
                new ArrayList<>(ids),
                NotificationKind.PAYMENT_CONFIRMED,
                "Pagamento confirmado: " + tripName,
                "Pagamento recebido" + (amountLabel.isBlank() ? "" : " (" + amountLabel.trim() + ")")
                        + " para \"" + tripName + "\".",
                "PAYMENT",
                payment.id,
                true);
    }

    private TripPaymentKind resolveKind(ProposalCheckoutRequest request, Trip trip) {
        TripPaymentKind requested = request != null && request.getKind() != null
                ? request.getKind()
                : TripPaymentKind.DEPOSIT;
        if (requested == TripPaymentKind.BALANCE) {
            BigDecimal paid = tripPaymentRepository.sumPaidByTrip(trip.id);
            BigDecimal remaining = trip.getFinalPrice().subtract(paid);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Não há saldo restante para cobrar");
            }
            return TripPaymentKind.BALANCE;
        }
        if (requested == TripPaymentKind.FULL) {
            return TripPaymentKind.FULL;
        }
        return TripPaymentKind.DEPOSIT;
    }

    private BigDecimal resolveAmount(Trip trip, TripPaymentKind kind) {
        BigDecimal finalPrice = trip.getFinalPrice().setScale(2, RoundingMode.HALF_UP);
        return switch (kind) {
            case FULL -> finalPrice;
            case DEPOSIT -> finalPrice.multiply(DEFAULT_DEPOSIT_RATIO).setScale(2, RoundingMode.HALF_UP);
            case BALANCE -> {
                BigDecimal paid = tripPaymentRepository.sumPaidByTrip(trip.id);
                yield finalPrice.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }
        };
    }

    private boolean isStripeConfigured() {
        return apiKey.isPresent() && !apiKey.get().isBlank();
    }

    private String defaultProposalSuccessUrl(String shareCode) {
        String base = appPublicUrl != null ? appPublicUrl.trim().replaceAll("/$", "") : "http://localhost:3000";
        return base + "/payment/success?shareCode=" + shareCode;
    }

    private String defaultProposalCancelUrl(String shareCode) {
        String base = appPublicUrl != null ? appPublicUrl.trim().replaceAll("/$", "") : "http://localhost:3000";
        return base + "/p/" + shareCode;
    }

    private Set<String> allowedRedirectOrigins() {
        return Arrays.stream(corsOriginsConfig.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .collect(Collectors.toSet());
    }

    private String resolveRedirectUrl(String requestedUrl, String fallbackUrl) {
        if (requestedUrl == null || requestedUrl.isBlank()) {
            return fallbackUrl;
        }
        try {
            URI uri = URI.create(requestedUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return fallbackUrl;
            }
            if (!"https".equalsIgnoreCase(scheme)
                    && !("http".equalsIgnoreCase(scheme) && "localhost".equalsIgnoreCase(host))) {
                return fallbackUrl;
            }
            int port = uri.getPort();
            String origin = port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
            if (!allowedRedirectOrigins().contains(origin)) {
                return fallbackUrl;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return fallbackUrl;
            }
            String query = uri.getRawQuery();
            return query != null && !query.isBlank() ? origin + path + "?" + query : origin + path;
        } catch (Exception e) {
            return fallbackUrl;
        }
    }

    private static String withSessionIdParam(String url) {
        return url + (url.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
    }

    private static String resolveIdempotencyKey(String headerValue, String fallback) {
        String key = headerValue != null && !headerValue.isBlank() ? headerValue.trim() : fallback;
        if (key.length() > 255) {
            return key.substring(0, 255);
        }
        return key;
    }
}
