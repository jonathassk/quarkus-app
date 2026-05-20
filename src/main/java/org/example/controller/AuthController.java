package org.example.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.TokenService;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;

@Slf4j
@Path("/api/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @GET
    @Path("/me")
    public Response getAuthenticatedUser(@HeaderParam("Authorization") String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("GET /auth/me rejected: missing or invalid Authorization header");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Missing or invalid Authorization header")
                    .build();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        try {
            String userIdStr = tokenService.validateToken(token);
            Long userId = Long.valueOf(userIdStr);

            User user = userRepository.findById(userId);
            if (user == null) {
                log.warn("GET /auth/me rejected: user not found for userId={}", userId);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("User not found")
                        .build();
            }

            UserResponseDTO response = UserResponseDTO.builder()
                    .token(token)
                    .refreshToken(null)
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .fullname(user.getFullName())
                    .id(user.id)
                    .expiresIn(null)
                    .build();

            return Response.ok(response).build();
        } catch (Exception e) {
            log.warn("GET /auth/me rejected: invalid or expired token ({})", e.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or expired token")
                    .build();
        }
    }
}
