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
import org.example.application.dto.payment.response.PaymentResponseDTO;
import org.example.application.services.TokenService;
import org.example.domain.entity.Trip;
import org.example.domain.entity.User;
import org.example.domain.entity.Workspace;
import org.example.domain.entity.WorkspaceMember;
import org.example.domain.enums.UserType;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.utils.RequestAuthHeaders;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
            return origin + path;
        } catch (Exception e) {
            log.warn("Invalid Stripe redirect URL: {}", requestedUrl, e);
            return fallbackUrl;
        }
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
        }

        try {
            String resolvedSuccessUrl = resolveRedirectUrl(request.getSuccessUrl(), successUrl);
            String resolvedCancelUrl = resolveRedirectUrl(request.getCancelUrl(), cancelUrl);

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setSuccessUrl(resolvedSuccessUrl + "?session_id={CHECKOUT_SESSION_ID}")
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
                paramsBuilder.setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .putMetadata("targetId", request.getTargetId().toString())
                                .putMetadata("paymentType", request.getPaymentType())
                                .build()
                );
            }

            // Metadados na sessão de checkout
            paramsBuilder.putMetadata("targetId", request.getTargetId().toString());
            paramsBuilder.putMetadata("paymentType", request.getPaymentType());

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

        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (session != null) {
                        String targetIdStr = session.getMetadata().get("targetId");
                        String paymentType = session.getMetadata().get("paymentType");
                        if (targetIdStr != null && paymentType != null) {
                            processSuccessfulPayment(UUID.fromString(targetIdStr), paymentType);
                        }
                    }
                    break;

                case "invoice.paid":
                    Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (invoice != null && invoice.getSubscription() != null) {
                        Subscription sub = Subscription.retrieve(invoice.getSubscription());
                        String targetIdStr = sub.getMetadata().get("targetId");
                        String paymentType = sub.getMetadata().get("paymentType");
                        if (targetIdStr != null && paymentType != null) {
                            processSuccessfulPayment(UUID.fromString(targetIdStr), paymentType);
                        }
                    }
                    break;

                case "customer.subscription.deleted":
                    Subscription subDeleted = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (subDeleted != null) {
                        String targetIdStr = subDeleted.getMetadata().get("targetId");
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
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Webhook processing failed: " + e.getMessage())
                    .build();
        }

        return Response.ok().build();
    }

    @Transactional
    public void processSuccessfulPayment(UUID targetId, String paymentType) {
        log.info("Processing successful payment: targetId={}, paymentType={}", targetId, paymentType);
        if ("MENSAL".equals(paymentType) || "ANUAL".equals(paymentType)) {
            Workspace workspace = Workspace.findById(targetId);
            if (workspace != null) {
                workspace.setPlanType("B2C_PREMIUM");
                workspace.persist();
                upgradeWorkspaceMembersUserType(targetId, UserType.PREMIUM);
                log.info("Workspace {} updated to B2C_PREMIUM", targetId);
            }
        } else if ("MENSAL_TRIP_AGENT".equals(paymentType) || "ANUAL_TRIP_AGENT".equals(paymentType)) {
            Workspace workspace = Workspace.findById(targetId);
            if (workspace != null) {
                workspace.setPlanType("B2B_PRO");
                workspace.persist();
                upgradeWorkspaceMembersUserType(targetId, UserType.PREMIUM);
                log.info("Workspace {} updated to B2B_PRO", targetId);
            }
        } else if ("UNITARIO".equals(paymentType)) {
            log.info("Single trip payment verified for Trip ID: {}", targetId);
            // Implementação futura caso precise de colunas extras ou liberação específica na Trip
        }
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
        }
    }

    private String getPriceIdForType(String type) {
        switch (type) {
            case "MENSAL":
                return priceMensal.orElse("");
            case "ANUAL":
                return priceAnual.orElse("");
            case "MENSAL_TRIP_AGENT":
                return priceMensalAgent.orElse("");
            case "ANUAL_TRIP_AGENT":
                return priceAnualAgent.orElse("");
            default:
                return null;
        }
    }
}
