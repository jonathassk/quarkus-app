package org.example.infrastructure.http;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.exception.event.EventException;

import java.util.HashMap;
import java.util.Map;

@Provider
public class EventExceptionMapper implements ExceptionMapper<EventException> {

    @Override
    public Response toResponse(EventException exception) {
        Object entity;
        if (exception.getEventId() != null) {
            Map<String, Object> body = new HashMap<>();
            body.put("code", exception.getCode());
            body.put("message", exception.getMessage());
            body.put("eventId", exception.getEventId());
            entity = body;
        } else {
            entity =
                    ApiErrorBody.builder()
                            .code(exception.getCode())
                            .message(exception.getMessage())
                            .build();
        }
        return Response.status(exception.getStatus()).entity(entity).build();
    }
}
