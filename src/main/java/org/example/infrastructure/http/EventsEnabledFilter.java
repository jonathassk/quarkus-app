package org.example.infrastructure.http;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.common.ApiErrorBody;

import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class EventsEnabledFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "events.enabled", defaultValue = "true")
    boolean eventsEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (eventsEnabled) {
            return;
        }
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("api/v1/events") || path.startsWith("/api/v1/events")) {
            requestContext.abortWith(
                    Response.status(503)
                            .entity(
                                    ApiErrorBody.builder()
                                            .code("EVENTS_DISABLED")
                                            .message("Events feature is disabled")
                                            .build())
                            .build());
        }
    }
}
