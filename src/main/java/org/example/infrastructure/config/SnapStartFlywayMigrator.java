package org.example.infrastructure.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.flywaydb.core.Flyway;

/**
 * Executes Flyway migrations after AWS Lambda SnapStart restore.
 * This avoids network calls during the snapshot/initialization phase.
 *
 * <p><b>ATENÇÃO — limite de tempo do restore (~10s):</b> o hook {@code afterRestore}
 * do SnapStart tem um orçamento rígido de ~10 segundos. {@code flyway.migrate()} aqui
 * funciona bem quando não há nada pendente (só valida, rápido) ou quando a migration
 * pendente é leve. Migrations PESADAS (ex.: o baseline UUID, ~12s) estouram esse limite
 * e o restore entra em loop de timeout — o schema nunca é aplicado.
 *
 * <p>Por isso, o fluxo recomendado é aplicar as migrations FORA da Lambda antes do deploy,
 * via {@code scripts/db-migrate.sh} (ou {@code scripts/deploy.sh}, que já faz isso).
 * Depois de aplicadas, este método vira um no-op rápido (apenas validação).
 */
@Slf4j
@ApplicationScoped
public class SnapStartFlywayMigrator implements Resource {

    @Inject
    Flyway flyway;

    void onStart(@Observes StartupEvent event) {
        log.info("Registering SnapStartFlywayMigrator with CRaC context");
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        log.info("SnapStart checkpoint initiated. Preparing for snapshot...");
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        log.info("SnapStart restore completed. Running database migrations...");
        try {
            // Idempotente: se o banco já estiver na versão certa (aplicado por
            // scripts/db-migrate.sh), isto apenas valida e retorna rápido. Migrations
            // pesadas devem ser pré-aplicadas fora da Lambda para não estourar o
            // limite de ~10s do restore.
            flyway.migrate();
            log.info("Database migrations completed successfully after restore.");
        } catch (Exception e) {
            log.error("Failed to execute database migrations after restore", e);
            throw e; // Fail initialization of restored container if migrations fail
        }
    }
}
