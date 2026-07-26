package org.example.infrastructure.http;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.example.application.exception.EntitlementExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class EntitlementExceptionMapper implements ExceptionMapper<EntitlementExceededException> {

    @Override
    public Response toResponse(EntitlementExceededException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", e.getCode());
        body.put("message", e.getMessage());
        body.put("feature", e.getFeature());
        body.put("planType", e.getPlanType());
        body.put("limit", e.getLimit());
        body.put("used", e.getUsed());
        body.put("upgradePath", "/settings/billing");
        return Response.status(402).entity(body).build();
    }
}
