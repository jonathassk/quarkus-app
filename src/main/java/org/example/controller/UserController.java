package org.example.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.example.adapters.rest.UserControllerAdapter;
import org.example.application.dto.UserRequestDTO;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.application.usecases.interfaces.CreateUserUseCase;

@Path("/api/v1/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserController {
    private final UserControllerAdapter adapter;

    public UserController(UserControllerAdapter adapter) {
        this.adapter = adapter;
    }

    @POST
    @Path("/create-user")
    public String createUserEmail() {

        return "created";
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}