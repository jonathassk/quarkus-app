package org.example.infrastructure.http;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.common.ApiErrorBody;
import org.example.utils.AuthTokenException;

@Slf4j
@Provider
public class AuthExceptionMapper implements ExceptionMapper<AuthTokenException> {

    @Override
    public Response toResponse(AuthTokenException exception) {
        log.warn("Auth rejected code={} message={}", exception.getCode(), exception.getMessage());
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(
                        ApiErrorBody.builder()
                                .code(exception.getCode())
                                .message(exception.getMessage())
                                .build())
                .build();
    }
}
