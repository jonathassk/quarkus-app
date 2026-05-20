package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.trip.request.NameDescriptionTravelRequestDto;
import org.example.application.dto.trip.request.TripRequestDTO;
import org.example.application.dto.trip.request.UserInlcudeRequestDTO;
import org.example.application.dto.trip.response.TripResponseDTO;
import org.example.application.services.TokenService;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.domain.entity.Trip;
import org.example.domain.repository.TripRepository;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.TripMapper;
import org.example.utils.TripDataValidator;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Path("/api/v1/trips")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class TripController {

    private final CreateTripUseCase createTripUseCase;
    private final UpdateTripUseCase updateTripUseCase;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TokenService tokenService;

    private static final String UNAUTHORIZED_MSG = "Invalid or expired token";
    private static final String AUTH_HEADER_MSG = "Missing or invalid Authorization header";
    private static final String FORBIDDEN_TRIP_MSG = "You do not have access to this trip";

    private Optional<Long> resolveAuthenticatedUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            String token = authorizationHeader.substring("Bearer ".length()).trim();
            Long userId = Long.valueOf(tokenService.validateToken(token));
            if (userRepository.findById(userId) == null) {
                log.warn("Auth failed: user not found for userId={}", userId);
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (Exception e) {
            log.warn("Auth failed: invalid token ({})", e.getMessage());
            return Optional.empty();
        }
    }

    private Response unauthorizedResponse() {
        return Response.status(Response.Status.UNAUTHORIZED).entity(UNAUTHORIZED_MSG).build();
    }

    private Response missingAuthHeaderResponse() {
        return Response.status(Response.Status.UNAUTHORIZED).entity(AUTH_HEADER_MSG).build();
    }

    private Response ensureTripMember(Long tripId, String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("Trip access denied: tripId={}, reason=missing_auth_header", tripId);
            return missingAuthHeaderResponse();
        }
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(authorizationHeader);
        if (userIdOpt.isEmpty()) {
            log.warn("Trip access denied: tripId={}, reason=invalid_token", tripId);
            return unauthorizedResponse();
        }
        Trip trip = tripRepository.findById(tripId);
        if (trip == null) {
            log.warn("Trip access denied: tripId={}, reason=trip_not_found", tripId);
            return Response.status(Response.Status.NOT_FOUND).entity("Trip not found").build();
        }
        if (!tripRepository.isUserLinkedToTrip(tripId, userIdOpt.get())) {
            log.warn("Trip access denied: tripId={}, userId={}, reason=not_member", tripId, userIdOpt.get());
            return Response.status(Response.Status.FORBIDDEN).entity(FORBIDDEN_TRIP_MSG).build();
        }
        return null;
    }

    @GET
    @Transactional(Transactional.TxType.REQUIRED)
    public Response listTripsForCurrentUser(@HeaderParam("Authorization") String authorizationHeader) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(authorizationHeader);
        if (userIdOpt.isEmpty()) {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                log.warn("List trips rejected: missing auth header");
                return missingAuthHeaderResponse();
            }
            log.warn("List trips rejected: invalid token");
            return unauthorizedResponse();
        }
        List<TripResponseDTO> trips = tripRepository.findAllByLinkedUserId(userIdOpt.get()).stream()
                .map(TripMapper::mapToTripResponseDTO)
                .collect(Collectors.toList());
        return Response.ok(trips).build();
    }

    @POST
    @Transactional
    @Path("/create-trip")
    public Response createTrip(@Valid TripRequestDTO tripRequest) {
        try {
            TripDataValidator.validateTripRequest(tripRequest);
            if (userRepository.findById(tripRequest.getCreatedBy()) == null) {
                log.warn("Create trip rejected: creator not found, createdBy={}", tripRequest.getCreatedBy());
                return Response.status(Response.Status.BAD_REQUEST).entity("User not found").build();
            }
            Trip result = createTripUseCase.createTrip(tripRequest);
            return Response.status(Response.Status.CREATED).entity(result.id).build();
        } catch (IllegalArgumentException e) {
            log.warn("Create trip rejected: name={}, reason={}", tripRequest.getName(), e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            log.warn("Create trip rejected: {}", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Create trip failed: name={}, createdBy={}", tripRequest.getName(), tripRequest.getCreatedBy(), e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{tripId}")
    @Transactional(Transactional.TxType.REQUIRED)
    public Response getTripById(@PathParam("tripId") Long tripId,
                               @HeaderParam("Authorization") String authorizationHeader) {
        Response denied = ensureTripMember(tripId, authorizationHeader);
        if (denied != null) {
            return denied;
        }
        Trip trip = tripRepository.findById(tripId);
        TripResponseDTO tripResponse = TripMapper.mapToTripResponseDTO(trip);
        return Response.ok(tripResponse).build();
    }

    @PUT
    @Transactional
    @Path("/{tripId}/update-trip")
    public Response updateTrip(@PathParam("tripId") Long tripId,
                              @Valid TripRequestDTO tripRequest,
                              @HeaderParam("Authorization") String authorizationHeader) {
        Response denied = ensureTripMember(tripId, authorizationHeader);
        if (denied != null) {
            return denied;
        }
        try {
            TripDataValidator.validateTripRequest(tripRequest);
            Trip updatedTrip = updateTripUseCase.updateTrip(tripId, tripRequest);
            TripResponseDTO tripResponse = TripMapper.mapToTripResponseDTO(updatedTrip);
            return Response.ok(tripResponse).build();
        } catch (NotFoundException e) {
            log.warn("Update trip rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            log.warn("Update trip rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update trip failed: tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while updating the trip: " + e.getMessage())
                    .build();
        }
    }

    @PATCH
    @Transactional
    @Path("/{tripId}/update-name-description")
    public Response updateTripNameAndDescription(@PathParam("tripId") Long tripId,
                                                  @Valid NameDescriptionTravelRequestDto request,
                                                  @HeaderParam("Authorization") String authorizationHeader) {
        Response denied = ensureTripMember(tripId, authorizationHeader);
        if (denied != null) {
            return denied;
        }
        try {
            if (request.getName() == null || request.getDescription() == null
                    || request.getName().isBlank() || request.getDescription().isBlank()) {
                log.warn("Update trip name/description rejected: tripId={}, reason=empty_fields", tripId);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Name and description cannot be null or empty")
                        .build();
            }
            Trip updatedTrip = updateTripUseCase.updateNameAndDescription(tripId, request);
            TripResponseDTO tripResponse = TripMapper.mapToTripResponseDTO(updatedTrip);
            return Response.ok(tripResponse).build();
        } catch (NotFoundException e) {
            log.warn("Update trip name/description rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update trip name/description failed: tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while updating the trip: " + e.getMessage())
                    .build();
        }
    }

    @PATCH
    @Path("/{tripId}/update-users-trip")
    @Transactional
    public Response updateUsersTrip(@PathParam("tripId") Long tripId,
                                   @Valid List<UserInlcudeRequestDTO> request,
                                   @HeaderParam("Authorization") String authorizationHeader) {
        Response denied = ensureTripMember(tripId, authorizationHeader);
        if (denied != null) {
            return denied;
        }
        try {
            request.forEach(userRequest -> {
                if (userRequest.getUserId() == null || userRequest.getPermissionLevel() == null) {
                    throw new IllegalArgumentException("User ID and permission level cannot be null");
                }
            });
            Trip updatedTrip = updateTripUseCase.updateUsersTrip(tripId, request);
            return Response.status(201).entity(updatedTrip).build();
        } catch (IllegalArgumentException e) {
            log.warn("Update trip users rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            log.warn("Update trip users rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Update trip users failed: tripId={}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while updating trip users: " + e.getMessage())
                    .build();
        }
    }

    @DELETE
    @Path("/{tripId}")
    @Transactional
    public Response deleteTrip(@PathParam("tripId") Long tripId,
                            @HeaderParam("Authorization") String authorizationHeader) {
        Optional<Long> userIdOpt = resolveAuthenticatedUserId(authorizationHeader);
        if (userIdOpt.isEmpty()) {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                log.warn("Delete trip rejected: tripId={}, reason=missing_auth_header", tripId);
                return missingAuthHeaderResponse();
            }
            log.warn("Delete trip rejected: tripId={}, reason=invalid_token", tripId);
            return unauthorizedResponse();
        }
        try {
            updateTripUseCase.deleteTrip(tripId, userIdOpt.get());
            return Response.noContent().build();
        } catch (NotFoundException e) {
            log.warn("Delete trip rejected: tripId={}, reason={}", tripId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ForbiddenException e) {
            log.warn("Delete trip rejected: tripId={}, userId={}, reason={}", tripId, userIdOpt.get(), e.getMessage());
            return Response.status(Response.Status.FORBIDDEN).entity(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Delete trip failed: tripId={}, userId={}", tripId, userIdOpt.get(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while deleting the trip: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/test")
    public String test() {
        return "teste";
    }
}
