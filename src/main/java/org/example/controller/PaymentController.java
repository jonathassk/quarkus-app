package org.example.controller;

import java.util.UUID;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.payment.request.PaymentRequestDTO;
import org.example.application.dto.payment.request.ReconcilePaymentRequestDTO;
import org.example.application.dto.payment.response.PaymentResponseDTO;
import org.example.application.services.TokenService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.entity.Workspace;
import org.example.domain.entity.WorkspaceMember;
import org.example.domain.enums.UserType;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.application.services.agency.AgencyService;
import org.example.application.services.payment.StripeEventService;
import org.example.application.services.payment.TripUnlockService;
import org.example.application.services.proposal.ProposalPaymentService;
import org.example.utils.RequestAuthHeaders;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.application.services.entitlement.EntitlementService;

@Slf4j
@Tag(name = "Payments", description = "Integração com Stripe para pagamentos unitários de viagens e assinaturas de planos")
@Path("/api/v1/payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PaymentController {

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TokenService tokenService;
    private final AgencyService agencyService;
    private final StripeEventService stripeEventService;
    private final TripUnlockService tripUnlockService;
    private final ProposalPaymentService proposalPaymentService;

    @ConfigProperty(name = "stripe.api.key")
    Optional<String> apiKey;

    @ConfigProperty(name = "stripe.webhook.secret")
    Optional<String> webhookSecret;

    @ConfigProperty(name = "stripe.success.url")
    String successUrl;

    @ConfigProperty(name = "stripe.cancel.url")
    String cancelUrl;

    @ConfigProperty(name = "stripe.price.mensal")
    Optional<String> priceMensal;

    @ConfigProperty(name = "stripe.price.anual")
    Optional<String> priceAnual;

    @ConfigProperty(name = "stripe.price.mensal-agent")
    Optional<String> priceMensalAgent;

    @ConfigProperty(name = "stripe.price.anual-agent")
    Optional<String> priceAnualAgent;

    @ConfigProperty(name = "stripe.price.mensal-agent-starter")
    Optional<String> priceMensalAgentStarter;

    @ConfigProperty(name = "stripe.price.anual-agent-starter")
    Optional<String> priceAnualAgentStarter;

    @ConfigProperty(name = "stripe.price.mensal-agent-team")
    Optional<String> priceMensalAgentTeam;

    @ConfigProperty(name = "stripe.price.anual-agent-team")
    Optional<String> priceAnualAgentTeam;

    @ConfigProperty(name = "stripe.trial.period-days", defaultValue = "5")
    int trialPeriodDays;

    @ConfigProperty(name = "quarkus.http.cors.origins", defaultValue = "http://localhost:3000")
    String corsOriginsConfig;

    @PostConstruct
    void init() {
        String key = apiKey.orElse("").trim();
        if (key.isEmpty()) {
            log.error("SECURITY: STRIPE_API_KEY não configurado — pagamentos estarão indisponíveis.");
        } else {
            Stripe.apiKey = key;
        }
        String secret = webhookSecret.orElse("").trim();
        if (secret.isEmpty()) {
            log.error("SECURITY: STRIPE_WEBHOOK_SECRET não configurado — webhooks serão rejeitados.");
        }
    }

    /** Retorna 503 se o Stripe não estiver configurado com chaves reais. */
    private Response stripeNotConfiguredResponse() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("Payment service is not configured. Contact support.")
                .build();
    }

    private boolean isStripeConfigured() {
        return apiKey.isPresent() && !apiKey.get().isBlank() &&
               webhookSecret.isPresent() && !webhookSecret.get().isBlank();
    }

    private Set<String> allowedRedirectOrigins() {
        return Arrays.stream(corsOriginsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
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
                log.warn("Rejected Stripe redirect URL with disallowed origin: {}", origin);
                return fallbackUrl;
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return fallbackUrl;
            }
            String query = uri.getRawQuery();
            return query != null && !query.isBlank() ? origin + path + "?" + query : origin + path;
        } catch (Exception e) {
            log.warn("Invalid Stripe redirect URL: {}", requestedUrl, e);
            return fallbackUrl;
        }
    }

    /** O placeholder do Stripe é resolvido no redirect; a query já validada é preservada. */
    private static String withSessionIdParam(String url) {
        return url + (url.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
    }

    private Optional<UUID> resolveAuthenticatedUserId(HttpHeaders headers) {
        String bearerLine = RequestAuthHeaders.resolveBearerHeaderLine(
                headers != null ? headers.getHeaderString(HttpHeaders.AUTHORIZATION) : null,
                headers != null ? headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION) : null
        );
        if (bearerLine == null) {
            return Optional.empty();
        }
        try {
            String token = bearerLine.substring("Bearer ".length()).trim();
            UUID userId = UUID.fromString(tokenService.validateToken(token));
            if (userRepository.findById(userId) == null) {
                log.warn("Payment Auth failed: user not found for userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Payment Auth failed: invalid token ({})", e.getMessage());
            return Optional.empty();
        }
    }

    @POST
    @Path("/checkout-session")
    @Operation(
        summary = "Criar sessão de checkout do Stripe",
        description = "Gera a URL de checkout do Stripe para pagamento unitário de viagem (UNITARIO) ou assinaturas de planos (MENSAL, ANUAL, etc.). " +
                      "Requer autenticação via Bearer token."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "URL da sessão de checkout gerada com sucesso"),
        @APIResponse(responseCode = "400", description = "Parâmetros inválidos ou Price ID não configurado"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "430", description = "Acesso proibido à viagem ou workspace"),
        @APIResponse(responseCode = "503", description = "Serviço de pagamentos desabilitado ou sem chaves de API")
    })
    public Response createCheckoutSession(
        @RequestBody(description = "Dados de pagamento (paymentType e targetId)", required = true) PaymentRequestDTO request, 
        @Context HttpHeaders headers) {
        if (!isStripeConfigured()) {
            return stripeNotConfiguredResponse();
        }
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid or expired token").build();
        }

        if (request.getPaymentType() == null || request.getTargetId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("paymentType and targetId are required").build();
        }

        boolean withTrial = Boolean.TRUE.equals(request.getTrial());
        if (withTrial && "UNITARIO".equals(request.getPaymentType())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Trial is only available for subscription plans")
                    .build();
        }
        if (withTrial && !isTrialAllowedPaymentType(request.getPaymentType())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Trial is only available for monthly entry plans (Premium or Essencial)")
                    .build();
        }

        User authUser = userRepository.findById(userIdOpt.get());
        if (authUser == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid or expired token").build();
        }

        // 1. Validar permissão dependendo do tipo
        if ("UNITARIO".equals(request.getPaymentType())) {
            Trip trip = tripRepository.findById(request.getTargetId());
            if (trip == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Trip not found").build();
            }
            if (!tripRepository.isUserLinkedToTrip(request.getTargetId(), userIdOpt.get())) {
                return Response.status(Response.Status.FORBIDDEN).entity("You do not have access to this trip").build();
            }
        } else {
            // Assinatura
            WorkspaceMember member = WorkspaceMember.find("workspace.id = ?1 and user.id = ?2", request.getTargetId(), userIdOpt.get()).firstResult();
            if (member == null) {
                return Response.status(Response.Status.FORBIDDEN).entity("You are not a member of this workspace").build();
            }
            if (withTrial) {
                Workspace workspace = member.getWorkspace();
                String planType = workspace != null ? workspace.getPlanType() : "FREE";
                if (!EntitlementService.isTrialEligible(authUser, planType)) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Trial already used or an active plan is in effect")
                            .build();
                }
            }
        }

        try {
            String resolvedSuccessUrl = resolveRedirectUrl(request.getSuccessUrl(), successUrl);
            String resolvedCancelUrl = resolveRedirectUrl(request.getCancelUrl(), cancelUrl);

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setSuccessUrl(withSessionIdParam(resolvedSuccessUrl))
                    .setCancelUrl(resolvedCancelUrl);

            if ("UNITARIO".equals(request.getPaymentType())) {
                paramsBuilder.setMode(SessionCreateParams.Mode.PAYMENT);
                paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("brl")
                                                .setUnitAmount(4990L) // R$ 49,90 default
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Roteiro de Viagem Premium (Código: " + request.getTargetId() + ")")
                                                                .build()
                                                )
                                                .build()
                                )
                                .setQuantity(1L)
                                .build()
                );
            } else {
                paramsBuilder.setMode(SessionCreateParams.Mode.SUBSCRIPTION);
                String priceId = getPriceIdForType(request.getPaymentType());
                if (priceId == null || priceId.isEmpty()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity("Price ID not configured/found for payment type: " + request.getPaymentType())
                            .build();
                }
                paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                );

                // Copiar metadados para a assinatura criada pelo Checkout
                SessionCreateParams.SubscriptionData.Builder subData = SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("targetId", request.getTargetId().toString())
                        .putMetadata("paymentType", request.getPaymentType())
                        .putMetadata("userId", userIdOpt.get().toString());
                if (withTrial) {
                    long days = Math.max(1L, trialPeriodDays);
                    subData.setTrialPeriodDays(days);
                    subData.putMetadata("trial", "true");
                }
                paramsBuilder.setSubscriptionData(subData.build());
            }

            // Metadados na sessão de checkout
            paramsBuilder.putMetadata("targetId", request.getTargetId().toString());
            paramsBuilder.putMetadata("paymentType", request.getPaymentType());
            paramsBuilder.putMetadata("userId", userIdOpt.get().toString());
            if (withTrial) {
                paramsBuilder.putMetadata("trial", "true");
            }

            Session session = Session.create(paramsBuilder.build());

            return Response.ok(new PaymentResponseDTO(session.getUrl())).build();

        } catch (Exception e) {
            log.error("Error creating Stripe checkout session", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error starting payment: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/webhook")
    @Consumes(MediaType.TEXT_PLAIN) // Webhooks brutos vêm como texto
    @Operation(
        summary = "Webhook do Stripe",
        description = "Endpoint público para receber eventos assíncronos do Stripe (checkout.session.completed, invoice.paid, etc.). " +
                      "Valida a assinatura do Stripe usando o Stripe-Signature header."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Evento processado com sucesso"),
        @APIResponse(responseCode = "400", description = "Assinatura inválida ou payload malformado"),
        @APIResponse(responseCode = "503", description = "Serviço Stripe não configurado")
    })
    public Response handleWebhook(
        String payload, 
        @HeaderParam("Stripe-Signature") String sigHeader) {
        if (!isStripeConfigured()) {
            log.error("Stripe webhook recebido mas Stripe não está configurado.");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        if (sigHeader == null || sigHeader.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing Stripe-Signature header").build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret.orElse(""));
        } catch (SignatureVerificationException e) {
            log.warn("Stripe Signature Verification failed", e);
            return Response.status(Response.Status.BAD_REQUEST).entity("Signature verification failed").build();
        }

        log.info("Received Stripe webhook event: {}", event.getType());

        if (!stripeEventService.claim(event.getId(), event.getType())) {
            log.info("Stripe event {} already processed — skipping (type={})", event.getId(), event.getType());
            return Response.ok().build();
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (session != null) {
                        applyCheckoutSession(session);
                    }
                    break;

                case "customer.subscription.updated":
                    Subscription subUpdated = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (subUpdated != null) {
                        handleSubscriptionStatusChange(subUpdated);
                    }
                    break;

                case "invoice.paid":
                    Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (invoice != null && invoice.getSubscription() != null) {
                        Subscription sub = Subscription.retrieve(invoice.getSubscription());
                        applySubscriptionPayment(sub, false);
                    }
                    break;

                case "customer.subscription.deleted":
                    Subscription subDeleted = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (subDeleted != null) {
                        String targetIdStr = subDeleted.getMetadata() != null
                                ? subDeleted.getMetadata().get("targetId")
                                : null;
                        if (targetIdStr != null) {
                            processSubscriptionCancellation(UUID.fromString(targetIdStr));
                        }
                    }
                    break;

                default:
                    log.debug("Unhandled event type: {}", event.getType());
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing Stripe webhook event: {}", event.getType(), e);
            stripeEventService.release(event.getId());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Webhook processing failed: " + e.getMessage())
                    .build();
        }

        return Response.ok().build();
    }

    /**
     * Reaplica o plano a partir de uma session/subscription Stripe já paga.
     * Corrige casos em que o webhook falhou ou o Price ID indica plano de agência
     * enquanto o metadata (ou UI de billing) apontava Premium B2C.
     */
    @POST
    @Path("/reconcile")
    @Operation(
        summary = "Reconciliar plano com Stripe",
        description = "Lê a Checkout Session ou Subscription no Stripe e reaplica o plano no workspace/agência. "
                + "O tipo efetivo prioriza o Price ID cobrado (fonte da verdade do produto)."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Plano reconciliado"),
        @APIResponse(responseCode = "400", description = "Parâmetros inválidos ou sessão sem assinatura"),
        @APIResponse(responseCode = "401", description = "Não autenticado"),
        @APIResponse(responseCode = "403", description = "Sessão/assinatura de outro usuário"),
        @APIResponse(responseCode = "503", description = "Stripe não configurado")
    })
    public Response reconcilePayment(
            @RequestBody(required = true) ReconcilePaymentRequestDTO request,
            @Context HttpHeaders headers) {
        if (!isStripeConfigured()) {
            return stripeNotConfiguredResponse();
        }
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid or expired token").build();
        }
        boolean hasSession = request != null && request.getSessionId() != null && !request.getSessionId().isBlank();
        boolean hasSub = request != null && request.getSubscriptionId() != null && !request.getSubscriptionId().isBlank();
        if (!hasSession && !hasSub) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("sessionId or subscriptionId is required")
                    .build();
        }
        try {
            if (hasSession) {
                Session session = Session.retrieve(
                        request.getSessionId().trim(),
                        SessionRetrieveParams.builder().addExpand("subscription").build(),
                        null);
                String metaUser = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
                if (metaUser != null && !metaUser.isBlank() && !userIdOpt.get().toString().equals(metaUser.trim())) {
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("Checkout session does not belong to this user")
                            .build();
                }
                if (!userOwnsCheckoutTarget(userIdOpt.get(), session)) {
                    return Response.status(Response.Status.FORBIDDEN)
                            .entity("You do not own the workspace/trip for this checkout")
                            .build();
                }
                applyCheckoutSession(session);
                String metaPt = session.getMetadata() != null ? session.getMetadata().get("paymentType") : null;
                String priceId = null;
                if (session.getSubscription() != null && !session.getSubscription().isBlank()) {
                    priceId = extractPriceIdFromSubscription(Subscription.retrieve(session.getSubscription()));
                }
                String paymentType = resolveEffectivePaymentType(metaPt, priceId);
                return Response.ok(java.util.Map.of(
                        "ok", true,
                        "paymentType", paymentType != null ? paymentType : "",
                        "planType", paymentTypeToPlanType(paymentType)
                )).build();
            }

            Subscription sub = Subscription.retrieve(request.getSubscriptionId().trim());
            String metaUser = sub.getMetadata() != null ? sub.getMetadata().get("userId") : null;
            if (metaUser != null && !metaUser.isBlank() && !userIdOpt.get().toString().equals(metaUser.trim())) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("Subscription does not belong to this user")
                        .build();
            }
            if (!userOwnsSubscriptionTarget(userIdOpt.get(), sub)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("You do not own the workspace for this subscription")
                        .build();
            }
            applySubscriptionPayment(sub, isTrialingSubscription(sub));
            String paymentType = resolveEffectivePaymentType(
                    sub.getMetadata() != null ? sub.getMetadata().get("paymentType") : null,
                    extractPriceIdFromSubscription(sub));
            return Response.ok(java.util.Map.of(
                    "ok", true,
                    "paymentType", paymentType != null ? paymentType : "",
                    "planType", paymentTypeToPlanType(paymentType)
            )).build();
        } catch (Exception e) {
            log.error("Failed to reconcile Stripe payment", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Reconcile failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Dados da sessão de checkout necessários para cumprir um pagamento avulso.
     * Nulo nos fluxos de assinatura, que não geram desbloqueio por viagem.
     */
    public record CheckoutFulfillment(String sessionId, UUID userId, java.math.BigDecimal amount, String currency) {}

    private static CheckoutFulfillment fulfillmentOf(Session session) {
        UUID userId = null;
        String userIdStr = session.getMetadata().get("userId");
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                userId = UUID.fromString(userIdStr.trim());
            } catch (IllegalArgumentException e) {
                log.warn("Checkout session {} has invalid userId metadata: {}", session.getId(), userIdStr);
            }
        }
        java.math.BigDecimal amount = session.getAmountTotal() != null
                ? java.math.BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2)
                : null;
        String currency = session.getCurrency() != null ? session.getCurrency().toUpperCase() : null;
        return new CheckoutFulfillment(session.getId(), userId, amount, currency);
    }

    @Transactional
    public void processSuccessfulPayment(UUID targetId, String paymentType) {
        processSuccessfulPayment(targetId, paymentType, null, null, null, false);
    }

    @Transactional
    public void processSuccessfulPayment(UUID targetId, String paymentType, String stripeSubscriptionId) {
        processSuccessfulPayment(targetId, paymentType, stripeSubscriptionId, null, null, false);
    }

    @Transactional
    public void processSuccessfulPayment(UUID targetId, String paymentType, String stripeSubscriptionId,
                                         CheckoutFulfillment fulfillment) {
        processSuccessfulPayment(targetId, paymentType, stripeSubscriptionId, fulfillment, null, false);
    }

    @Transactional
    public void processSuccessfulPayment(UUID targetId, String paymentType, String stripeSubscriptionId,
                                         CheckoutFulfillment fulfillment, String tripPaymentIdStr) {
        processSuccessfulPayment(targetId, paymentType, stripeSubscriptionId, fulfillment, tripPaymentIdStr, false);
    }

    @Transactional
    public void processSuccessfulPayment(UUID targetId, String paymentType, String stripeSubscriptionId,
                                         CheckoutFulfillment fulfillment, String tripPaymentIdStr, boolean markTrialUsed) {
        log.info("Processing successful payment: targetId={}, paymentType={}, sub={}, trial={}",
                targetId, paymentType, stripeSubscriptionId, markTrialUsed);
        if (ProposalPaymentService.PAYMENT_TYPE_PROPOSAL.equals(paymentType)) {
            if (tripPaymentIdStr == null || tripPaymentIdStr.isBlank()) {
                log.warn("PROPOSAL payment without tripPaymentId metadata targetId={}", targetId);
                return;
            }
            proposalPaymentService.fulfillProposalPayment(
                    UUID.fromString(tripPaymentIdStr.trim()),
                    fulfillment != null ? fulfillment.sessionId() : null,
                    fulfillment != null ? fulfillment.amount() : null,
                    fulfillment != null ? fulfillment.currency() : null);
            return;
        }
        if ("MENSAL".equals(paymentType) || "ANUAL".equals(paymentType)) {
            Workspace workspace = Workspace.findById(targetId);
            if (workspace != null) {
                workspace.setPlanType("B2C_PREMIUM");
                workspace.persist();
                upgradeWorkspaceMembersUserType(targetId, UserType.PREMIUM);
                if (markTrialUsed) {
                    markTrialUsedForWorkspace(workspace, fulfillment != null ? fulfillment.userId() : null);
                }
                log.info("Workspace {} updated to B2C_PREMIUM", targetId);
            }
        } else if (isAgencyPaymentType(paymentType)) {
            Workspace workspace = Workspace.findById(targetId);
            if (workspace != null) {
                String agencyPlan = resolveAgencyPlanType(paymentType);
                workspace.setPlanType(agencyPlan);
                workspace.persist();
                upgradeWorkspaceMembersUserType(targetId, UserType.PREMIUM);
                activateAgencyForWorkspaceOwner(workspace, stripeSubscriptionId, agencyPlan);
                if (markTrialUsed) {
                    markTrialUsedForWorkspace(workspace, fulfillment != null ? fulfillment.userId() : null);
                }
                log.info("Workspace {} updated to {}", targetId, agencyPlan);
            }
        } else if ("UNITARIO".equals(paymentType)) {
            tripUnlockService.grantUnitario(
                    targetId,
                    fulfillment != null ? fulfillment.userId() : null,
                    fulfillment != null ? fulfillment.sessionId() : null,
                    fulfillment != null ? fulfillment.amount() : null,
                    fulfillment != null ? fulfillment.currency() : null);
        }
    }

    /**
     * Mantém o plano alinhado ao status da assinatura Stripe (trialing/active vs cancelado).
     */
    @Transactional
    public void handleSubscriptionStatusChange(Subscription sub) {
        if (sub == null) {
            return;
        }
        String status = sub.getStatus() != null ? sub.getStatus().toLowerCase() : "";
        if ("active".equals(status) || "trialing".equals(status)) {
            applySubscriptionPayment(sub, isTrialingSubscription(sub));
            return;
        }
        String targetIdStr = sub.getMetadata() != null ? sub.getMetadata().get("targetId") : null;
        if (targetIdStr == null || targetIdStr.isBlank()) {
            return;
        }
        if ("canceled".equals(status) || "unpaid".equals(status) || "incomplete_expired".equals(status)) {
            processSubscriptionCancellation(UUID.fromString(targetIdStr));
        }
    }

    /** Aplica upgrade a partir de checkout.session.completed (ou reconcile da session). */
    private void applyCheckoutSession(Session session) throws Exception {
        if (session == null || session.getMetadata() == null) {
            return;
        }
        String targetIdStr = session.getMetadata().get("targetId");
        String metaPaymentType = session.getMetadata().get("paymentType");
        String subscriptionId = session.getSubscription();
        String tripPaymentIdStr = session.getMetadata().get("tripPaymentId");
        boolean isTrial = "true".equalsIgnoreCase(session.getMetadata().get("trial"));

        String priceId = null;
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            Subscription sub = Subscription.retrieve(subscriptionId);
            priceId = extractPriceIdFromSubscription(sub);
            if (!isTrial) {
                isTrial = isTrialingSubscription(sub)
                        || "true".equalsIgnoreCase(sub.getMetadata() != null ? sub.getMetadata().get("trial") : null);
            }
        }
        String paymentType = resolveEffectivePaymentType(metaPaymentType, priceId);
        if (targetIdStr != null && paymentType != null) {
            processSuccessfulPayment(UUID.fromString(targetIdStr), paymentType, subscriptionId,
                    fulfillmentOf(session), tripPaymentIdStr, isTrial);
        } else {
            log.warn("Checkout session {} missing targetId/paymentType (meta={}, price→{})",
                    session.getId(), metaPaymentType, priceId);
        }
    }

    /** Aplica upgrade a partir de invoice.paid / subscription.updated / reconcile. */
    private void applySubscriptionPayment(Subscription sub, boolean markTrial) {
        if (sub == null || sub.getMetadata() == null) {
            return;
        }
        String targetIdStr = sub.getMetadata().get("targetId");
        String metaPaymentType = sub.getMetadata().get("paymentType");
        String priceId = extractPriceIdFromSubscription(sub);
        String paymentType = resolveEffectivePaymentType(metaPaymentType, priceId);
        if (targetIdStr == null || targetIdStr.isBlank() || paymentType == null || paymentType.isBlank()) {
            log.warn("Subscription {} missing targetId/paymentType (meta={}, price→{})",
                    sub.getId(), metaPaymentType, priceId);
            return;
        }
        processSuccessfulPayment(UUID.fromString(targetIdStr), paymentType, sub.getId(), null, null, markTrial);
    }

    private boolean userOwnsCheckoutTarget(UUID userId, Session session) {
        if (session.getMetadata() == null) {
            return false;
        }
        String targetIdStr = session.getMetadata().get("targetId");
        String paymentType = session.getMetadata().get("paymentType");
        if (targetIdStr == null || targetIdStr.isBlank()) {
            return false;
        }
        UUID targetId = UUID.fromString(targetIdStr.trim());
        if ("UNITARIO".equals(paymentType) || ProposalPaymentService.PAYMENT_TYPE_PROPOSAL.equals(paymentType)) {
            return tripRepository.isUserLinkedToTrip(targetId, userId);
        }
        WorkspaceMember member = WorkspaceMember
                .find("workspace.id = ?1 and user.id = ?2", targetId, userId)
                .firstResult();
        return member != null;
    }

    private boolean userOwnsSubscriptionTarget(UUID userId, Subscription sub) {
        if (sub.getMetadata() == null) {
            return false;
        }
        String targetIdStr = sub.getMetadata().get("targetId");
        if (targetIdStr == null || targetIdStr.isBlank()) {
            return false;
        }
        WorkspaceMember member = WorkspaceMember
                .find("workspace.id = ?1 and user.id = ?2", UUID.fromString(targetIdStr.trim()), userId)
                .firstResult();
        return member != null;
    }

    private static boolean isTrialingSubscription(Subscription sub) {
        if (sub == null) {
            return false;
        }
        if ("trialing".equalsIgnoreCase(sub.getStatus())) {
            return true;
        }
        return sub.getMetadata() != null && "true".equalsIgnoreCase(sub.getMetadata().get("trial"));
    }

    /**
     * Price ID cobrado no Stripe é a fonte da verdade do produto.
     * Se metadata e price divergirem (ex.: UI de billing mandou {@code ANUAL} mas o price é de agência),
     * prevalece o price — evita ficar preso em {@code B2C_PREMIUM} sem portal B2B.
     */
    private String resolveEffectivePaymentType(String metadataPaymentType, String priceId) {
        String fromPrice = resolvePaymentTypeFromPriceId(priceId);
        if (fromPrice != null) {
            if (metadataPaymentType != null && !metadataPaymentType.isBlank()
                    && !fromPrice.equals(metadataPaymentType.trim())) {
                log.warn("paymentType metadata={} disagrees with priceId {} → {}; preferring price",
                        metadataPaymentType, priceId, fromPrice);
            }
            return fromPrice;
        }
        return metadataPaymentType != null && !metadataPaymentType.isBlank()
                ? metadataPaymentType.trim()
                : null;
    }

    private String resolvePaymentTypeFromPriceId(String priceId) {
        if (priceId == null || priceId.isBlank()) {
            return null;
        }
        String id = priceId.trim();
        if (id.equals(priceMensal.orElse(""))) return "MENSAL";
        if (id.equals(priceAnual.orElse(""))) return "ANUAL";
        if (id.equals(priceMensalAgentStarter.orElse(""))) return "MENSAL_TRIP_AGENT_STARTER";
        if (id.equals(priceAnualAgentStarter.orElse(""))) return "ANUAL_TRIP_AGENT_STARTER";
        if (id.equals(priceMensalAgent.orElse(""))) return "MENSAL_TRIP_AGENT";
        if (id.equals(priceAnualAgent.orElse(""))) return "ANUAL_TRIP_AGENT";
        if (id.equals(priceMensalAgentTeam.orElse(""))) return "MENSAL_TRIP_AGENT_TEAM";
        if (id.equals(priceAnualAgentTeam.orElse(""))) return "ANUAL_TRIP_AGENT_TEAM";
        return null;
    }

    private static String extractPriceIdFromSubscription(Subscription sub) {
        if (sub == null || sub.getItems() == null || sub.getItems().getData() == null
                || sub.getItems().getData().isEmpty()) {
            return null;
        }
        var item = sub.getItems().getData().get(0);
        if (item == null || item.getPrice() == null) {
            return null;
        }
        return item.getPrice().getId();
    }

    private static String paymentTypeToPlanType(String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            return "";
        }
        if ("MENSAL".equals(paymentType) || "ANUAL".equals(paymentType)) {
            return "B2C_PREMIUM";
        }
        if (isAgencyPaymentType(paymentType)) {
            return resolveAgencyPlanType(paymentType);
        }
        return paymentType;
    }

    private void markTrialUsedForWorkspace(Workspace workspace, UUID checkoutUserId) {
        User user = null;
        if (checkoutUserId != null) {
            user = userRepository.findById(checkoutUserId);
        }
        if (user == null && workspace != null) {
            WorkspaceMember ownerMember = WorkspaceMember
                    .find("workspace.id = ?1 and role = ?2", workspace.id, org.example.domain.enums.WorkspaceRole.OWNER)
                    .firstResult();
            if (ownerMember == null) {
                ownerMember = WorkspaceMember.find("workspace.id", workspace.id).firstResult();
            }
            if (ownerMember != null) {
                user = ownerMember.getUser();
            }
        }
        if (user != null && user.getTrialUsedAt() == null) {
            user.setTrialUsedAt(Instant.now());
            user.persist();
            log.info("Marked trial_used_at for user {}", user.id);
        }
    }

    /**
     * Garante Agency + membership OWNER para o dono do workspace.
     */
    private void activateAgencyForWorkspaceOwner(
            Workspace workspace, String stripeSubscriptionId, String agencyPlanType) {
        WorkspaceMember ownerMember = WorkspaceMember
                .find("workspace.id = ?1 and role = ?2", workspace.id, org.example.domain.enums.WorkspaceRole.OWNER)
                .firstResult();
        if (ownerMember == null) {
            ownerMember = WorkspaceMember.find("workspace.id", workspace.id).firstResult();
        }
        if (ownerMember == null || ownerMember.getUser() == null) {
            log.warn("No workspace member to attach Agency for workspace={}", workspace.id);
            return;
        }
        User owner = ownerMember.getUser();
        String agencyName = workspace.getName() != null && !workspace.getName().isBlank()
                ? workspace.getName()
                : (owner.getFullName() != null ? owner.getFullName() : "Agência");
        var agency = agencyService.ensureAgencyForOwner(owner, agencyName);
        agencyService.activateSubscription(agency, stripeSubscriptionId, agencyPlanType);
        log.info("Agency {} activated as {} for user {}", agency.id, agencyPlanType, owner.id);
    }

    /**
     * Atualiza o {@code userType} de todos os membros do workspace para o tipo informado.
     * Usado para refletir, ao nível do usuário, o upgrade de plano feito no workspace após um pagamento confirmado.
     */
    private void upgradeWorkspaceMembersUserType(UUID workspaceId, UserType userType) {
        List<WorkspaceMember> members = WorkspaceMember.find("workspace.id", workspaceId).list();
        for (WorkspaceMember member : members) {
            User user = member.getUser();
            if (user != null && user.getUserType() != userType) {
                user.setUserType(userType);
                user.persist();
                log.info("User {} userType updated to {}", user.getId(), userType);
            }
        }
    }

    @Transactional
    public void processSubscriptionCancellation(UUID targetId) {
        log.info("Processing subscription cancellation: targetId={}", targetId);
        Workspace workspace = Workspace.findById(targetId);
        if (workspace != null) {
            workspace.setPlanType("FREE");
            workspace.persist();
            log.info("Workspace {} downgraded to FREE", targetId);

            WorkspaceMember ownerMember = WorkspaceMember.find("workspace.id", workspace.id).firstResult();
            if (ownerMember != null && ownerMember.getUser() != null) {
                agencyService.requireMembership(ownerMember.getUser().id).ifPresent(m ->
                        agencyService.downgradeSubscription(m.getAgency()));
            }
        }
    }

    private String getPriceIdForType(String type) {
        switch (type) {
            case "MENSAL":
                return priceMensal.orElse("");
            case "ANUAL":
                return priceAnual.orElse("");
            case "MENSAL_TRIP_AGENT_STARTER":
                return priceMensalAgentStarter.orElse("");
            case "ANUAL_TRIP_AGENT_STARTER":
                return priceAnualAgentStarter.orElse("");
            case "MENSAL_TRIP_AGENT":
                return priceMensalAgent.orElse("");
            case "ANUAL_TRIP_AGENT":
                return priceAnualAgent.orElse("");
            case "MENSAL_TRIP_AGENT_TEAM":
                return priceMensalAgentTeam.orElse("");
            case "ANUAL_TRIP_AGENT_TEAM":
                return priceAnualAgentTeam.orElse("");
            default:
                return null;
        }
    }

    private static boolean isAgencyPaymentType(String paymentType) {
        return "MENSAL_TRIP_AGENT_STARTER".equals(paymentType)
                || "ANUAL_TRIP_AGENT_STARTER".equals(paymentType)
                || "MENSAL_TRIP_AGENT".equals(paymentType)
                || "ANUAL_TRIP_AGENT".equals(paymentType)
                || "MENSAL_TRIP_AGENT_TEAM".equals(paymentType)
                || "ANUAL_TRIP_AGENT_TEAM".equals(paymentType);
    }

    /** Trial só nos planos mensais de entrada (Premium B2C e Essencial B2B). */
    private static boolean isTrialAllowedPaymentType(String paymentType) {
        return "MENSAL".equals(paymentType) || "MENSAL_TRIP_AGENT_STARTER".equals(paymentType);
    }

    /** Essencial → B2B_STARTER; Solo → B2B_PRO; Team → B2B_TEAM. */
    private static String resolveAgencyPlanType(String paymentType) {
        if (paymentType != null && paymentType.contains("STARTER")) {
            return "B2B_STARTER";
        }
        if (paymentType != null && paymentType.contains("TEAM")) {
            return "B2B_TEAM";
        }
        return "B2B_PRO";
    }
}
