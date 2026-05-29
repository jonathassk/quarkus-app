package org.example.infrastructure.http;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.dto.common.ApiErrorBody;
import org.example.utils.AuthTokenException;

public final class AuthApiResponses {

    private AuthApiResponses() {}

    public static Response unauthorized(String code, String message) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiErrorBody.builder().code(code).message(message).build())
                .build();
    }

    public static Response unauthorized(AuthTokenException e) {
        return unauthorized(e.getCode(), e.getMessage());
    }
}
