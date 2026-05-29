package org.example.infrastructure.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;

/**
 * Loga o estado da aplicação na primeira invocação real da Lambda.
 *
 * <p>Com SnapStart, {@code @PostConstruct} roda durante a criação do snapshot — os logs
 * desse momento <em>não aparecem</em> no CloudWatch. Este listener observa o
 * {@link StartupEvent} que o Quarkus dispara após o restore do snapshot (na invocação
 * real), garantindo que o estado do {@link NeonAuthJwtVerifier} seja visível nos logs.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class StartupLogger {

    private final NeonAuthJwtVerifier neonAuthJwtVerifier;

    void onStart(@Observes StartupEvent event) {
        log.info("=== Baggagi Lambda startup ===");
        log.info("Neon Auth verifier configured: {}", neonAuthJwtVerifier.isConfigured());
        if (neonAuthJwtVerifier.isConfigured()) {
            log.info("Neon Auth expectedIssuer : {}", neonAuthJwtVerifier.getExpectedIssuer());
            log.info("Neon Auth jwksUrl        : {}", neonAuthJwtVerifier.getJwksUrl());
        } else {
            log.warn("Neon Auth verifier NOT configured — session-sync will reject all tokens!");
        }
        log.info("==============================");
    }
}
