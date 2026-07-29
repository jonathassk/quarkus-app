package org.example.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

/**
 * Ping público leve que abre uma conexão JDBC e executa {@code SELECT 1}.
 * Usado pelo frontend no bootstrap para acordar Lambda + Neon antes de páginas com dados.
 */
@Slf4j
@Tag(name = "Warmup", description = "Aquecimento de Lambda + banco (sem autenticação)")
@Path("/api/v1/public/warmup")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class WarmupController {

    private final DataSource dataSource;

    @GET
    @Path("/db")
    @Operation(summary = "Acorda o pool JDBC / Neon (SELECT 1)")
    public Response db() {
        long startNs = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            if (!rs.next()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("ok", false, "error", "empty_result"))
                        .build();
            }
            long dbMs = (System.nanoTime() - startNs) / 1_000_000L;
            return Response.ok(Map.of("ok", true, "dbMs", dbMs)).build();
        } catch (Exception e) {
            log.warn("DB warmup failed: {}", e.toString());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("ok", false, "error", "db_unavailable"))
                    .build();
        }
    }
}
