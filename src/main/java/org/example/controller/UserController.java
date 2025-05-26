package org.example.controller;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.UserRequestDTO;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.utils.UserDataVerification;

import java.util.logging.LogManager;
import java.util.logging.Logger;

@Path("/api/v1/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LogManager.getLogManager().getLogger(UserController.class.getName());

    private final UserDataVerification userDataVerification;
    private final CreateUserUseCase createUserUseCase;

    @POST
    @Path("/create-user")
    public Response createUserEmail(UserRequestDTO dto) {
        logger.info("Received request to create user, email" + dto.email());
        if (!userDataVerification.verifyUserData(dto).isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(userDataVerification.verifyUserData(dto))
                .build();
        }
        logger.info("User empty data verified successfully");
        createUserUseCase.createUserEmail(dto);
        return null;
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}