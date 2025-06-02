package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.TripDataValidator;

@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripController {

    private final CreateTripUseCase createTripUseCase;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;

    @POST
    @Transactional
    @Path("/create-trip")
    public Response createTrip(@Valid TripRequestDTO tripRequest) {
        TripDataValidator.validateTripRequest(tripRequest);
        Trip result = createTripUseCase.createTrip(tripRequest);

        return Response.status(Response.Status.CREATED)
                .entity(result.id)
                .build();
    }

    @GET
    @Path("/{tripId}")
    public Response getTripById(@PathParam("tripId") Long tripId) {
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) return Response.status(Response.Status.NOT_FOUND).build();
        TripResponseDTO tripResponse = TripMapper.mapToTripResponseDTO(trip);
        return Response.ok(tripResponse).build();
    }
}
