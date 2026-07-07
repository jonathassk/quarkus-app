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
            flyway.migrate();
            log.info("Database migrations completed successfully after restore.");
        } catch (Exception e) {
            log.error("Failed to execute database migrations after restore", e);
            throw e; // Fail initialization of restored container if migrations fail
        }
    }
}
