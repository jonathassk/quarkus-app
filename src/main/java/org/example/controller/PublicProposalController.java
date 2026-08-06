package org.example.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.dto.common.ApiErrorBody;
import org.example.application.dto.proposal.ApprovePublicProposalRequest;
import org.example.application.dto.proposal.ProposalCheckoutRequest;
import org.example.application.dto.proposal.RejectPublicProposalRequest;
import org.example.application.dto.proposal.commercial.ApproveCommercialProposalRequest;
import org.example.application.dto.proposal.commercial.RequestChangeProposalRequest;
import org.example.application.services.proposal.CommercialProposalService;
import org.example.application.services.proposal.ProposalPaymentService;
import org.example.application.services.proposal.ProposalService;

@Slf4j
@Tag(name = "Public Proposals", description = "Proposta interativa white-label (sem autenticação)")
@Path("/api/v1/public/proposals")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PublicProposalController {

    private final ProposalService proposalService;
    private final ProposalPaymentService proposalPaymentService;
    private final CommercialProposalService commercialProposalService;

    @GET
    @Path("/{shareCode}")
    @Operation(summary = "Obter proposta pública por shareCode")
    public Response get(@PathParam("shareCode") String shareCode) {
        try {
            if (commercialProposalService.existsByShareCode(shareCode)) {
                return Response.ok(commercialProposalService.getPublic(shareCode)).build();
            }
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
    @Operation(summary = "Cliente aprova a proposta (aceite digital com nome, e-mail e opção)")
    public Response approve(
            @PathParam("shareCode") String shareCode,
            ApprovePublicProposalRequest body,
            @Context HttpHeaders headers) {
        try {
            if (commercialProposalService.existsByShareCode(shareCode)) {
                ApproveCommercialProposalRequest req = ApproveCommercialProposalRequest.builder()
                        .name(body != null ? body.getName() : null)
                        .email(body != null ? body.getEmail() : null)
                        .optionId(body != null ? body.getOptionId() : null)
                        .addonIds(body != null ? body.getAddonIds() : null)
                        .termsText(body != null ? body.getTermsText() : null)
                        .sessionId(body != null ? body.getSessionId() : null)
                        .build();
                // Fallback: legado envia tierCodes sem optionId — rejeita com mensagem clara
                if (req.getOptionId() == null) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiErrorBody.builder()
                                    .code("BAD_REQUEST")
                                    .message("optionId is required for multi-option proposals")
                                    .build())
                            .build();
                }
                return Response.ok(commercialProposalService.approvePublic(
                        shareCode, req, clientIp(headers), userAgent(headers))).build();
            }
            return Response.ok(proposalService.approvePublicProposal(
                    shareCode, body, clientIp(headers), userAgent(headers))).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                    .build();
        }
    }

    @POST
    @Path("/{shareCode}/request-change")
    @Transactional
    @Operation(summary = "Cliente solicita alteração na proposta")
    public Response requestChange(
            @PathParam("shareCode") String shareCode,
            RequestChangeProposalRequest body,
            @Context HttpHeaders headers) {
        try {
            if (!commercialProposalService.existsByShareCode(shareCode)) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiErrorBody.builder().code("NOT_FOUND").message("Proposal not found").build())
                        .build();
            }
            return Response.ok(commercialProposalService.requestChangePublic(
                    shareCode, body, clientIp(headers), userAgent(headers))).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                    .build();
        }
    }

    @POST
    @Path("/{shareCode}/reject")
    @Transactional
    @Operation(summary = "Cliente recusa a proposta com motivo")
    public Response reject(
            @PathParam("shareCode") String shareCode,
            RejectPublicProposalRequest body,
            @Context HttpHeaders headers) {
        try {
            if (commercialProposalService.existsByShareCode(shareCode)) {
                String reason = body != null ? body.getReason() : null;
                return Response.ok(commercialProposalService.rejectPublic(
                        shareCode, reason, clientIp(headers), userAgent(headers))).build();
            }
            return Response.ok(proposalService.rejectPublicProposal(
                    shareCode, body, clientIp(headers), userAgent(headers))).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                    .build();
        }
    }

    @POST
    @Path("/{shareCode}/checkout")
    @Transactional
    @Operation(summary = "Iniciar checkout Stripe (sinal ou valor cheio) após aprovação")
    public Response checkout(
            @PathParam("shareCode") String shareCode,
            ProposalCheckoutRequest body,
            @Context HttpHeaders headers) {
        try {
            String idempotencyKey =
                    headers != null ? headers.getHeaderString("Idempotency-Key") : null;
            return Response.ok(proposalPaymentService.startCheckout(shareCode, body, idempotencyKey))
                    .build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiErrorBody.builder().code("NOT_FOUND").message(e.getMessage()).build())
                    .build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(e.getMessage()).build())
                    .build();
        } catch (ServiceUnavailableException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder().code("PAYMENT_UNAVAILABLE").message(e.getMessage()).build())
                    .build();
        }
    }

    private static String clientIp(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return headers.getHeaderString("X-Real-IP");
    }

    private static String userAgent(HttpHeaders headers) {
        return headers != null ? headers.getHeaderString("User-Agent") : null;
    }
}
