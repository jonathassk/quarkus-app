package org.example.controller;

import io.quarkus.security.UnauthorizedException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.user.request.UserCreateRequestDTO;
import org.example.application.dto.user.request.UserLoginRequestDTO;
import org.example.application.dto.user.request.UserProfileUpdateRequestDTO;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.dto.user.response.UserProfileDTO;
import org.example.application.dto.user.response.UserSearchResultDTO;
import org.example.application.services.TokenService;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.utils.RequestAuthHeaders;
import org.example.utils.UserDataVerification;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Tag(name = "Users", description = "Gerenciamento de contas de usuário B2C, cadastro, login tradicional e perfil")
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
    private final UserRepository userRepository;
    private final TokenService tokenService;

    @POST
    @Transactional
    @Path("/create-user")
    @Operation(
        summary = "Criar usuário comum (B2C)",
        description = "Cadastra um novo usuário no sistema usando e-mail e senha tradicionais."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Usuário criado com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos ou e-mail já em uso"),
        @APIResponse(responseCode = "500", description = "Erro interno no servidor")
    })
    public Response createUserEmail(@RequestBody(description = "Dados para criação do usuário", required = true) UserCreateRequestDTO dto) {
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
    @Operation(
        summary = "Login tradicional por e-mail/senha",
        description = "Autentica um usuário e-mail/senha comum e retorna o token JWT no header Authorization e no body."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Login efetuado com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados de login ausentes"),
        @APIResponse(responseCode = "401", description = "Credenciais inválidas"),
        @APIResponse(responseCode = "500", description = "Erro interno")
    })
    public Response loginUser(@RequestBody(description = "Credenciais de login", required = true) UserLoginRequestDTO request) {
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

    /**
     * Search registered users to invite to a trip (min 2 characters).
     */
    @GET
    @Path("/search")
    @Operation(
        summary = "Buscar usuários para convite",
        description = "Busca usuários registrados no Baggagi que combinem com a query para serem convidados a uma viagem."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Resultados da busca retornados com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado")
    })
    public Response searchUsers(
            @QueryParam("q") String query,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }
        if (query == null || query.trim().length() < 2) {
            return Response.ok(List.of()).build();
        }
        List<UserSearchResultDTO> results =
                userRepository.searchForInvite(query, actorId.get(), 20).stream()
                        .map(this::toSearchResult)
                        .collect(Collectors.toList());
        if (results.isEmpty()) {
            log.warn("User search returned no results q={} actorId={}", query.trim(), actorId.get());
        }
        return Response.ok(results).build();
    }

    private UserSearchResultDTO toSearchResult(User user) {
        return UserSearchResultDTO.builder()
                .id(user.id)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .build();
    }

    private Optional<Long> resolveAuthenticatedUserId(HttpHeaders headers) {
        String bearerLine =
                headers != null
                        ? RequestAuthHeaders.resolveBearerHeaderLine(
                                headers.getHeaderString(HttpHeaders.AUTHORIZATION),
                                headers.getHeaderString(RequestAuthHeaders.BAGGAGI_AUTHORIZATION))
                        : null;
        if (bearerLine == null) {
            return Optional.empty();
        }
        try {
            String token = bearerLine.substring("Bearer ".length()).trim();
            Long userId = Long.valueOf(tokenService.validateToken(token));
            if (userRepository.findById(userId) == null) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @GET
    @Path("/auth/profile")
    @Operation(
        summary = "Obter perfil do usuário autenticado",
        description = "Retorna os detalhes do perfil do usuário autenticado no momento."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Dados do perfil retornados com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public Response getProfile(@Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }
        User user = userRepository.findById(actorId.get());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("User not found")
                    .build();
        }
        UserProfileDTO profile = UserProfileDTO.builder()
                .id(user.id)
                .email(user.getEmail())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatar(user.getProfilePictureUrl())
                .preferredLanguage(user.getPreferredLanguage())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
        return Response.ok(profile).build();
    }

    @PATCH
    @Path("/auth/profile")
    @Transactional
    @Operation(
        summary = "Atualizar perfil do usuário",
        description = "Atualiza o nome completo, o idioma ou outras configurações de perfil do usuário atual."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public Response updateProfile(
            @RequestBody(description = "Dados para atualização do perfil", required = true) UserProfileUpdateRequestDTO dto,
            @Context HttpHeaders headers) {
        Optional<Long> actorId = resolveAuthenticatedUserId(headers);
        if (actorId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }
        User user = userRepository.findById(actorId.get());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("User not found")
                    .build();
        }

        if (dto.getFullName() != null) {
            user.setFullName(dto.getFullName());
        }

        String newLang = dto.getLanguage() != null ? dto.getLanguage() : dto.getPreferredLanguage();
        if (newLang != null) {
            user.setPreferredLanguage(newLang);
        }

        userRepository.persist(user);

        UserProfileDTO profile = UserProfileDTO.builder()
                .id(user.id)
                .email(user.getEmail())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatar(user.getProfilePictureUrl())
                .preferredLanguage(user.getPreferredLanguage())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
        return Response.ok(profile).build();
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}
