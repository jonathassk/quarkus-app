package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.TripSegmentRequestDTO;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.entity.TripSegment;
import org.example.domain.entity.TripUser;
import org.example.domain.entity.User;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.example.utils.TripDataValidator;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;

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
        ModelMapper mapper = ModelMapperFactory.createModelMapper();

        TripDataValidator.validateTripRequest(tripRequest);
        Trip trip = mapper.map(tripRequest, Trip.class);

        User creator = userRepository.findById(tripRequest.getCreatedBy());
        if (creator == null) throw new NotFoundException("User not found");
        trip.setCreatedBy(creator);

        TripUser tripUser = new TripUser();
        tripUser.setUser(creator);
        tripUser.setTrip(trip);
        tripUser.setPermissionLevel("OWNER");

        if (tripRequest.getSegments() != null && !tripRequest.getSegments().isEmpty()) {
            List<TripSegment> segments = new ArrayList<>();

            for (TripSegmentRequestDTO segmentDTO : tripRequest.getSegments()) {
                TripSegment segment = mapper.map(segmentDTO, TripSegment.class);

                // Configura a relação BIDIRECIONAL
                segment.setTrip(trip); // LADO OBRIGATÓRIO
                segments.add(segment);
            }

            // Atribui a lista completa de segmentos
            trip.setSegments(segments);
        }

        List<TripUser> tripUsers = new ArrayList<>();
        tripUsers.add(tripUser);
        trip.setUsers(tripUsers);

        // 5. Persiste o Trip (os segmentos serão persistidos em cascata)
        tripRepository.persist(trip);

        return Response.status(Response.Status.CREATED)
                .entity("Trip created successfully")
                .build();
    }
}
