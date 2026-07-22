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
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.application.dto.document.UploadDocumentRequest;
import org.example.utils.DocumentUploadSupport;
import java.util.UUID;
import java.util.Map;

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
    private final ObjectStorageService objectStorageService;

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
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
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

    private Optional<UUID> resolveAuthenticatedUserId(HttpHeaders headers) {
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
            UUID userId = UUID.fromString(tokenService.validateToken(token));
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
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
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
        UserProfileDTO profile = toUserProfileDTO(user);
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
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
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

        if (dto.getPhoneNumber() != null) {
            user.setPhoneNumber(dto.getPhoneNumber());
        }

        if (dto.getGender() != null) {
            try {
                if (dto.getGender().trim().isEmpty()) {
                    user.setGender(null);
                } else {
                    user.setGender(org.example.domain.enums.Gender.valueOf(dto.getGender().toUpperCase()));
                }
            } catch (Exception e) {
                user.setGender(org.example.domain.enums.Gender.OTHER);
            }
        }

        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
        }

        if (dto.getDateOfBirth() != null) {
            if (dto.getDateOfBirth().trim().isEmpty()) {
                user.setDateOfBirth(null);
            } else {
                user.setDateOfBirth(java.time.LocalDate.parse(dto.getDateOfBirth()));
            }
        }

        if (dto.getCity() != null) {
            user.setCity(dto.getCity());
        }

        if (dto.getCountry() != null) {
            user.setCountry(dto.getCountry());
        }

        if (dto.getAvatar() != null) {
            user.setProfilePictureUrl(dto.getAvatar());
        }

        userRepository.persist(user);

        UserProfileDTO profile = toUserProfileDTO(user);
        return Response.ok(profile).build();
    }

    @GET
    @Path("/auth/visited-countries")
    @Operation(
        summary = "Obter países visitados pelo usuário autenticado",
        description = "Retorna a lista de países que o usuário autenticado já visitou."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de países retornada com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public Response getVisitedCountries(@Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
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
        return Response.ok(user.getVisitedCountries()).build();
    }

    @PUT
    @Path("/auth/visited-countries")
    @Transactional
    @Operation(
        summary = "Atualizar lista de países visitados",
        description = "Substitui a lista de países visitados do usuário autenticado pela nova lista fornecida."
    )
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Lista de países atualizada com sucesso"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public Response updateVisitedCountries(
            @RequestBody(description = "Lista de países visitados", required = true) List<String> visitedCountries,
            @Context HttpHeaders headers) {
        Optional<UUID> actorId = resolveAuthenticatedUserId(headers);
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
        user.setVisitedCountries(visitedCountries != null ? visitedCountries : List.of());
        userRepository.persist(user);
        return Response.ok(user.getVisitedCountries()).build();
    }

    @POST
    @Path("/auth/avatar-upload-request")
    @Transactional
    @Operation(
        summary = "Solicitar upload de avatar presignado",
        description = "Gera uma URL presignada para o frontend enviar a imagem de perfil diretamente ao Cloudflare R2 (S3)."
    )
    @APIResponses({
        @APIResponse(responseCode = "201", description = "URL presignada gerada com sucesso"),
        @APIResponse(responseCode = "400", description = "Dados inválidos ou tipo de arquivo não suportado"),
        @APIResponse(responseCode = "401", description = "Token inválido ou expirado"),
        @APIResponse(responseCode = "503", description = "Serviço de storage não configurado")
    })
    public Response avatarUploadRequest(
            @RequestBody(description = "Nome do arquivo e content type", required = true) UploadDocumentRequest req,
            @Context HttpHeaders headers) {
        Optional<UUID> userIdOpt = resolveAuthenticatedUserId(headers);
        if (userIdOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("code", "UNAUTHORIZED", "message", "Token inválido ou expirado"))
                    .build();
        }
        if (!objectStorageService.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("code", "STORAGE_NOT_CONFIGURED", "message", "Document storage is not configured"))
                    .build();
        }

        if (req == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "VALIDATION_ERROR", "message", "Request body is required"))
                    .build();
        }

        Optional<DocumentUploadSupport.ResolvedUpload> resolved =
                DocumentUploadSupport.resolve(req.getFileName(), req.getContentType());
        if (resolved.isEmpty()) {
            String msg = DocumentUploadSupport.unsupportedTypeMessage(
                    req.getContentType(), req.getFileName());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("code", "UNSUPPORTED_CONTENT_TYPE", "message", msg))
                    .build();
        }

        DocumentUploadSupport.ResolvedUpload upload = resolved.get();
        String extension = DocumentUploadSupport.extractExtension(upload.fileName());
        
        // S3 key: avatars/{userId}/avatar-{uuid}{extension}
        String s3Key = "avatars/" + userIdOpt.get() + "/avatar-" + UUID.randomUUID() + extension;

        try {
            String uploadUrl = objectStorageService.presignPut(s3Key, upload.contentType());
            String publicUrl = objectStorageService.getPublicUrl(s3Key);

            var body = Map.of(
                "uploadUrl", uploadUrl,
                "s3Key", s3Key,
                "publicUrl", publicUrl,
                "expiresInSeconds", objectStorageService.getUploadPresignSeconds()
            );

            return Response.status(Response.Status.CREATED).entity(body).build();
        } catch (Exception e) {
            log.error("Avatar upload request failed userId={}", userIdOpt.get(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("code", "INTERNAL_ERROR", "message", "Erro ao gerar URL presignada: " + e.getMessage()))
                    .build();
        }
    }

    private UserProfileDTO toUserProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.id)
                .email(user.getEmail())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatar(user.getProfilePictureUrl())
                .preferredLanguage(user.getPreferredLanguage())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .phoneNumber(user.getPhoneNumber())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .bio(user.getBio())
                .dateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null)
                .city(user.getCity())
                .country(user.getCountry())
                .visitedCountries(user.getVisitedCountries())
                .build();
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}
