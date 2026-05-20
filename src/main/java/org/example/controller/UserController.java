package org.example.controller;

import io.quarkus.security.UnauthorizedException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.user.request.UserCreateRequestDTO;
import org.example.application.dto.user.request.UserLoginRequestDTO;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.utils.UserDataVerification;

import java.util.function.Supplier;

@Slf4j
@Path("/api/v1/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class UserController {

    private static final int INTERNAL_ERROR_RETRY_MAX_ATTEMPTS = 3;
    private static final long INTERNAL_ERROR_RETRY_BASE_DELAY_MS = 150L;

    private final UserDataVerification userDataVerification;
    private final CreateUserUseCase createUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    @POST
    @Transactional
    @Path("/create-user")
    public Response createUserEmail(UserCreateRequestDTO dto) {
        if (!userDataVerification.verifyUserData(dto).isEmpty()) {
            log.warn("Create user rejected: validation failed for email={}", dto.getEmail());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(userDataVerification.verifyUserData(dto))
                    .build();
        }
        try {
            UserResponseDTO response = executeWithRetry(
                    () -> createUserUseCase.createUserEmail(dto),
                    "create user"
            );
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            log.warn("Create user rejected: email={}, reason={}", dto.getEmail(), e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Create user failed: email={}", dto.getEmail(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error creating user: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/login")
    public Response loginUser(UserLoginRequestDTO request) {
        if (request.getEmail() == null || request.getEmail().isEmpty()
                || request.getPassword() == null || request.getPassword().isEmpty()) {
            log.warn("Login rejected: missing email or password");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Email and password are required")
                    .build();
        }
        try {
            UserResponseDTO response = executeWithRetry(
                    () -> loginUserUseCase.LoginUserEmailUsername(request.getEmail(), request.getPassword()),
                    "login user"
            );
            return Response.status(Response.Status.OK)
                    .header("Authorization", "Bearer " + response.getToken())
                    .entity(response)
                    .build();
        } catch (UnauthorizedException | IllegalArgumentException e) {
            log.warn("Login rejected: email={}, reason={}", request.getEmail(), e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Login failed: email={}", request.getEmail(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error logging in user: " + e.getMessage())
                    .build();
        }
    }

    private <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= INTERNAL_ERROR_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (UnauthorizedException | IllegalArgumentException e) {
                throw e;
            } catch (RuntimeException e) {
                if (isNonRetryable(e)) {
                    log.error("{} aborted (non-retryable): {}", operationName, e.getMessage(), e);
                    throw e;
                }
                lastException = e;
                if (attempt == INTERNAL_ERROR_RETRY_MAX_ATTEMPTS) {
                    break;
                }
                long waitMs = INTERNAL_ERROR_RETRY_BASE_DELAY_MS * attempt;
                log.warn("{} transient failure (attempt {}/{}), retrying in {}ms: {}",
                        operationName, attempt, INTERNAL_ERROR_RETRY_MAX_ATTEMPTS, waitMs, e.getMessage());
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.error("{} retry interrupted", operationName, interruptedException);
                    throw new RuntimeException("Retry interrupted while trying to " + operationName, interruptedException);
                }
            }
        }
        log.error("{} failed after {} attempts", operationName, INTERNAL_ERROR_RETRY_MAX_ATTEMPTS, lastException);
        throw lastException != null ? lastException : new RuntimeException("Unknown error while trying to " + operationName);
    }

    private boolean isNonRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("password authentication failed")
                        || lower.contains("28p01")
                        || lower.contains("invalid authorization specification")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}
