package org.example.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.dto.user.request.SyncUserRequest;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;

import java.util.Map;
import java.util.Optional;

/**
 * Internal endpoints called exclusively by AWS Lambda Cognito triggers.
 * All requests must carry the X-Internal-Secret header matching the configured secret.
 */
@Slf4j
@Path("/api/v1/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CognitoController {

    private final UserRepository userRepository;
    private final String internalSecret;

    @Inject
    public CognitoController(
            UserRepository userRepository,
            @ConfigProperty(name = "internal.secret") String internalSecret) {
        this.userRepository = userRepository;
        this.internalSecret = internalSecret;
    }

    /**
     * POST /api/v1/users/sync
     *
     * Called by the Cognito post-confirmation Lambda after a user confirms their
     * account (email/password) or completes their first social login (Google).
     *
     * Returns 201 on creation, 200 on migration (existing user linked to Cognito),
     * 409 if the user was already synced.
     */
    @POST
    @Transactional
    @Path("/sync")
    public Response syncUser(
            @HeaderParam("X-Internal-Secret") String secret,
            SyncUserRequest req) {

        if (!internalSecret.equals(secret)) {
            log.warn("POST /users/sync rejected: invalid internal secret");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (req.getCognitoSub() == null || req.getCognitoSub().isBlank()
                || req.getEmail() == null || req.getEmail().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("cognitoSub and email are required")
                    .build();
        }

        Optional<User> existingBySub = userRepository.findByCognitoSub(req.getCognitoSub());
        if (existingBySub.isPresent()) {
            return Response.status(Response.Status.CONFLICT).build();
        }

        Optional<User> existingByEmail = userRepository.findByEmail(req.getEmail());
        if (existingByEmail.isPresent()) {
            // Migração: usuário já existia com email/senha, vincular ao Cognito
            User user = existingByEmail.get();
            user.setCognitoSub(req.getCognitoSub());
            if (req.getProvider() != null && !req.getProvider().isBlank()) {
                user.setProvider(req.getProvider());
            }
            if (req.getPictureUrl() != null && !req.getPictureUrl().isBlank()) {
                user.setProfilePictureUrl(req.getPictureUrl());
            }
            user.setEmailVerified(true);
            return Response.ok().build();
        }

        // Novo usuário
        String username = generateUniqueUsername(req.getEmail());
        String fullName = (req.getFullName() != null && !req.getFullName().isBlank())
                ? req.getFullName()
                : username;

        User newUser = User.builder()
                .cognitoSub(req.getCognitoSub())
                .email(req.getEmail())
                .fullName(fullName)
                .username(username)
                .provider(req.getProvider() != null ? req.getProvider() : "cognito")
                .profilePictureUrl(req.getPictureUrl())
                .emailVerified(true)
                .accountStatus("active")
                .role("USER")
                .build();

        userRepository.CreateUser(newUser);
        return Response.status(Response.Status.CREATED).build();
    }

    /**
     * GET /api/v1/users/claims?cognitoSub=xxx
     *
     * Called by the Cognito pre-token-generation Lambda to fetch the internal
     * user data that will be embedded as custom claims in the JWT.
     *
     * Returns { id, username, role } or 404 if not found.
     */
    @GET
    @Path("/claims")
    public Response getClaims(
            @HeaderParam("X-Internal-Secret") String secret,
            @QueryParam("cognitoSub") String cognitoSub) {

        if (!internalSecret.equals(secret)) {
            log.warn("GET /users/claims rejected: invalid internal secret");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (cognitoSub == null || cognitoSub.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("cognitoSub is required")
                    .build();
        }

        return userRepository.findByCognitoSub(cognitoSub)
                .map(user -> Response.ok(Map.of(
                        "id", user.id.toString(),
                        "username", user.getUsername() != null ? user.getUsername() : "",
                        "role", user.getRole() != null ? user.getRole() : "USER"
                )).build())
                .orElseGet(() -> {
                    log.warn("GET /users/claims: user not found for cognitoSub={}", cognitoSub);
                    return Response.status(Response.Status.NOT_FOUND).build();
                });
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .toLowerCase();
        if (base.length() < 3) {
            base = base + "_usr";
        }

        if (userRepository.findByUsername(base).isEmpty()) {
            return base;
        }

        for (int i = 2; i <= 999; i++) {
            String candidate = base + i;
            if (userRepository.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }

        return base + System.currentTimeMillis();
    }
}
