package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.user.request.UserCreateRequestDTO;
import org.example.application.dto.user.request.UserLoginRequestDTO;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.utils.UserDataVerification;
import org.jboss.resteasy.annotations.Body;

import java.util.logging.LogManager;
import java.util.logging.Logger;

@Path("/api/v1/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LogManager.getLogManager().getLogger(UserController.class.getName());

    private final UserDataVerification userDataVerification;
    private final CreateUserUseCase createUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    @POST
    @Transactional
    @Path("/create-user")
    public Response createUserEmail(UserCreateRequestDTO dto) {
        logger.info("Received request to create user, email" + dto.getEmail());
        if (!userDataVerification.verifyUserData(dto).isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(userDataVerification.verifyUserData(dto))
                .build();
        }
        try {
            UserResponseDTO response = createUserUseCase.createUserEmail(dto);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (Exception e) {
            logger.severe("Error creating user: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error creating user: " + e.getMessage())
                .build();
        }
    }

    @POST
    @Path("/login")
    public Response loginUser(UserLoginRequestDTO request) {
        logger.info("Received request to login user, email: " + request.getEmail());
        if (request.getEmail() == null || request.getEmail().isEmpty() || request.getPassword() == null || request.getPassword().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Email and password are required")
                .build();
        }

        try {
            UserResponseDTO response = loginUserUseCase.LoginUserEmailUsername(request.getEmail(), request.getPassword());
            return Response.status(Response.Status.OK).entity(response).build();
        } catch (Exception  e) {
            logger.severe("Error logging in user: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error logging in user: " + e.getMessage())
                .build();
        }
    }


    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}