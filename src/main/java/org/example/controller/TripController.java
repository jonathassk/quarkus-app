package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.domain.entity.Trip;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.example.utils.TripDataValidator;
import org.modelmapper.ModelMapper;

@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripController {

    @POST
    @Transactional
    @Path("/create-trip")
    public Response createTrip(@Valid TripRequestDTO tripRequest) {
        ModelMapper mapper = ModelMapperFactory.createModelMapper();
        TripDataValidator.validateTripRequest(tripRequest);
        Trip trip = mapper.map(tripRequest, Trip.class);
        return Response.status(Response.Status.CREATED)
                .entity("Trip created successfully")
                .build();
    }
}
