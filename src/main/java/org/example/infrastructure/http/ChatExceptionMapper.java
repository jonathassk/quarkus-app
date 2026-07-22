package org.example.infrastructure.http;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.exception.chat.ChatException;

@Slf4j
@Provider
public class ChatExceptionMapper implements ExceptionMapper<ChatException> {

    @Override
    public Response toResponse(ChatException exception) {
        if (exception.getStatus() >= 500) {
            log.error("Chat error: {}", exception.getMessage(), exception);
        }
        return Response.status(exception.getStatus())
                .entity(ApiErrorBody.builder()
                        .code(exception.getCode())
                        .message(exception.getMessage())
                        .build())
                .build();
    }
}
