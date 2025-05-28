package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.TripRequestDTO;

@Path("/api/v1/trip")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripController {

    @POST
    @Transactional
    @Path("/create-trip")
    public Response createTrip(TripRequestDTO tripRequest) {
        // Logic to handle trip creation
        // This could involve saving the trip details to a database, etc.

        // For now, just return a success response
        return Response.status(Response.Status.CREATED)
                .entity("Trip created successfully")
                .build();
    }
}
