package org.example.infrastructure.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Encaminha e-mails pontuais ao Lambda Go {@code email-worker} (SES),
 * fora do request path síncrono do Quarkus.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class EmailWorkerInvoker {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "email.worker.function-name")
    Optional<String> functionName;

    @ConfigProperty(name = "email.worker.enabled", defaultValue = "true")
    boolean enabled;

    public boolean isConfigured() {
        return enabled && functionName.isPresent() && !functionName.get().isBlank();
    }

    /**
     * Invoke assíncrono (Event). Retorna false se o worker não estiver configurado.
     */
    public boolean enqueueDirectEmail(String toEmail, String subject, String textBody, String htmlBody) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toEmail", toEmail);
        payload.put("subject", subject);
        payload.put("textBody", textBody != null ? textBody : "");
        payload.put("htmlBody", htmlBody != null ? htmlBody : "");
        return enqueue("send_direct", toEmail, payload);
    }

    /**
     * E-mail com a identidade visual da agência.
     *
     * @param templateKind {@code proposal_sent}, {@code boarding_reminder} ou {@code post_trip}.
     */
    public boolean enqueueWhiteLabelEmail(String toEmail, String agencyId, String templateKind,
                                          String tripName, String shareUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toEmail", toEmail);
        payload.put("agencyId", agencyId != null ? agencyId : "");
        payload.put("templateKind", templateKind);
        payload.put("tripName", tripName != null ? tripName : "");
        payload.put("shareUrl", shareUrl != null ? shareUrl : "");
        return enqueue("send_white_label", toEmail, payload);
    }

    /**
     * Enfileira qualquer action suportada pelo worker. O {@code action} é injetado no payload.
     */
    public boolean enqueue(String action, String toEmail, Map<String, Object> fields) {
        if (!isConfigured()) {
            log.info("email-worker not configured — skipping enqueue action={} to={}", action, toEmail);
            return false;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", action);
            if (fields != null) {
                fields.forEach((k, v) -> {
                    if (!"action".equals(k)) {
                        payload.put(k, v);
                    }
                });
            }

            byte[] json = objectMapper.writeValueAsBytes(payload);
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName.get().trim())
                    .invocationType(InvocationType.EVENT)
                    .payload(SdkBytes.fromByteArray(json))
                    .build();

            lambdaClient.invoke(request);
            log.info("email-worker enqueue ok action={} to={}", action, toEmail);
            return true;
        } catch (Exception e) {
            log.error("email-worker enqueue failed action={} to={}: {}", action, toEmail, e.getMessage());
            return false;
        }
    }
}
