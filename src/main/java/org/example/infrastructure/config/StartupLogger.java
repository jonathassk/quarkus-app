package org.example.infrastructure.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Valida variáveis de ambiente críticas na inicialização da aplicação.
 *
 * <p>Com SnapStart, {@code @PostConstruct} roda durante a criação do snapshot — os logs
 * desse momento <em>não aparecem</em> no CloudWatch. Este listener observa o
 * {@link StartupEvent} que o Quarkus dispara após o restore do snapshot (na invocação
 * real), garantindo que o estado seja visível nos logs e que segredos ausentes
 * causem falha imediata e explícita antes de qualquer request ser processado.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class StartupLogger {

    private final NeonAuthJwtVerifier neonAuthJwtVerifier;

    @ConfigProperty(name = "stripe.api.key", defaultValue = "")
    Optional<String> stripeApiKey;

    @ConfigProperty(name = "stripe.webhook.secret", defaultValue = "")
    Optional<String> stripeWebhookSecret;

    @ConfigProperty(name = "internal.secret", defaultValue = "")
    Optional<String> internalSecret;

    void onStart(@Observes StartupEvent event) {
        log.info("=== Baggagi startup ===");

        // Validação de segredos críticos — falha rápida e explícita
        List<String> missingSecrets = new ArrayList<>();

        String apiKey = stripeApiKey.orElse("").strip();
        if (apiKey.isEmpty()) {
            missingSecrets.add("STRIPE_API_KEY");
        } else if (apiKey.equals("sk_test_mock") || apiKey.equals("sk_live_mock")) {
            log.warn("SECURITY: STRIPE_API_KEY parece ser um valor mock/placeholder. " +
                    "Configure com uma chave real para o ambiente correto.");
        }

        String webhookSecret = stripeWebhookSecret.orElse("").strip();
        if (webhookSecret.isEmpty()) {
            missingSecrets.add("STRIPE_WEBHOOK_SECRET");
        } else if (webhookSecret.equals("whsec_mock")) {
            log.warn("SECURITY: STRIPE_WEBHOOK_SECRET é o valor mock padrão. " +
                    "Webhooks do Stripe serão rejeitados em produção sem um segredo real.");
        }

        String secret = internalSecret.orElse("").strip();
        if (secret.isEmpty()) {
            log.warn("SECURITY: INTERNAL_SECRET não está configurado. " +
                    "Rotas protegidas por segredo interno estarão vulneráveis.");
        } else if (secret.length() < 32) {
            log.warn("SECURITY: INTERNAL_SECRET tem menos de 32 caracteres (atual: {}). " +
                    "Use um segredo mais forte (openssl rand -hex 32).", secret.length());
        }

        // Neon Auth
        log.info("Neon Auth verifier configured: {}", neonAuthJwtVerifier.isConfigured());
        if (neonAuthJwtVerifier.isConfigured()) {
            log.info("Neon Auth expectedIssuer : {}", neonAuthJwtVerifier.getExpectedIssuer());
            log.info("Neon Auth jwksUrl        : {}", neonAuthJwtVerifier.getJwksUrl());
        } else {
            log.warn("SECURITY: Neon Auth verifier NOT configured — " +
                    "session-sync rejeitará todos os tokens! " +
                    "Defina NEON_AUTH_BASE_URL e NEON_AUTH_JWKS_URL.");
        }

        // Loga aviso crítico se segredos do Stripe estiverem ausentes.
        // NÃO derruba o Lambda — os endpoints de pagamento já retornam 503 quando não configurados.
        // Derrubar o Lambda impede todos os outros endpoints de funcionarem.
        if (!missingSecrets.isEmpty()) {
            log.error("SECURITY: segredos críticos do Stripe ausentes: {}. " +
                    "Pagamentos estarão indisponíveis — configure STRIPE_API_KEY e STRIPE_WEBHOOK_SECRET " +
                    "nas variáveis de ambiente do Lambda.", missingSecrets);
        }

        log.info("======================");
    }
}
