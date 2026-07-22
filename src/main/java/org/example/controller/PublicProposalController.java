package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.services.proposal.ProposalService;

@Slf4j
@Tag(name = "Public Proposals", description = "Proposta interativa white-label (sem autenticação)")
@Path("/api/v1/public/proposals")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PublicProposalController {

    private final ProposalService proposalService;

    @GET
    @Path("/{shareCode}")
    @Operation(summary = "Obter proposta pública por shareCode")
    public Response get(@PathParam("shareCode") String shareCode) {
        try {
            return Response.ok(proposalService.getPublicProposal(shareCode)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        }
    }

    @POST
    @Path("/{shareCode}/approve")
    @Transactional
    @Operation(summary = "Cliente aprova a proposta")
    public Response approve(@PathParam("shareCode") String shareCode) {
        try {
            return Response.ok(proposalService.approvePublicProposal(shareCode)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        }
    }
}
