package org.example.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.services.trip.TripShareLinkService;

@Slf4j
@Tag(name = "Public Trips", description = "Leitura pública de viagem via share link (sem autenticação)")
@Path("/api/v1/public/trips")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PublicTripController {

    private final TripShareLinkService shareLinkService;

    @GET
    @Path("/{code}")
    @Operation(summary = "Obter viagem pública por código do share link")
    public Response get(@PathParam("code") String code) {
        try {
            return Response.ok(shareLinkService.getPublicByCode(code)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        }
    }
}
