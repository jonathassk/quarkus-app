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
import org.example.application.dto.document.TripDocumentResponse;
import org.example.application.dto.passenger.PatchPublicPassengerFormRequest;
import org.example.application.dto.passenger.ResolvePassengerCorrectionRequest;
import org.example.application.dto.passenger.SubmitPublicPassengerFormRequest;
import org.example.application.services.passenger.TripPassengerService;
import org.example.domain.entity.TripDocument;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Public Passenger Forms", description = "Formulário de passageiro via token (sem login)")
@Path("/api/v1/public/passenger-forms")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PublicPassengerFormController {

    private final TripPassengerService passengerService;

    @GET
    @Path("/{token}")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(summary = "Carregar formulário público")
    public Response get(@PathParam("token") String token) {
        try {
            return Response.ok(passengerService.getPublicForm(token)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            log.error("Get public passenger form failed", e);
            return serverError();
        }
    }

    @PATCH
    @Path("/{token}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Salvar progresso do formulário")
    public Response patch(@PathParam("token") String token, PatchPublicPassengerFormRequest body) {
        try {
            return Response.ok(passengerService.patchPublicForm(token, body)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Patch public passenger form failed", e);
            return serverError();
        }
    }

    @POST
    @Path("/{token}/submit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Enviar formulário para a agência")
    public Response submit(
            @PathParam("token") String token, SubmitPublicPassengerFormRequest body) {
        try {
            return Response.ok(passengerService.submitPublicForm(token, body)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Submit public passenger form failed", e);
            return serverError();
        }
    }

    @POST
    @Path("/{token}/corrections/{correctionId}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Corrigir campo solicitado pela agência")
    public Response resolveCorrection(
            @PathParam("token") String token,
            @PathParam("correctionId") java.util.UUID correctionId,
            ResolvePassengerCorrectionRequest body) {
        try {
            return Response.ok(passengerService.resolveCorrectionByToken(token, correctionId, body))
                    .build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Resolve public correction failed", e);
            return serverError();
        }
    }

    @POST
    @Path("/{token}/apply-reusable-profile")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Aplicar perfil autorizado da agência no formulário")
    public Response applyReusableProfile(@PathParam("token") String token) {
        try {
            var form = passengerService.getPublicForm(token);
            if (!form.isReusableProfileAvailable()) {
                return badRequest("Nenhum perfil autorizado");
            }
            passengerService.applyReusableProfile(form.getTripId(), form.getPassengerId(), null);
            return Response.ok(passengerService.getPublicForm(token)).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Apply reusable profile failed", e);
            return serverError();
        }
    }

    @POST
    @Path("/{token}/documents")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @Operation(summary = "Upload de documento de identidade no cofre")
    public Response uploadDocument(
            @PathParam("token") String token,
            MultipartFormDataInput multipart) {
        try {
            Map<String, List<InputPart>> form = multipart.getFormDataMap();
            List<InputPart> fileParts = form.get("file");
            if (fileParts == null || fileParts.isEmpty()) {
                return badRequest("file is required");
            }
            InputPart filePart = fileParts.getFirst();
            String rawFileName = extractFileName(filePart);
            String browserContentType =
                    filePart.getMediaType() != null ? filePart.getMediaType().toString() : null;
            byte[] bytes = filePart.getBody(byte[].class, null);
            String title = firstFormValue(form, "title");
            String kind = firstFormValue(form, "documentKind");
            if (kind == null) {
                kind = firstFormValue(form, "kind");
            }

            TripDocument doc =
                    passengerService.uploadPublicDocument(
                            token, bytes, rawFileName, browserContentType, title, kind);

            TripDocumentResponse resp = TripDocumentResponse.builder()
                    .id(doc.id)
                    .tripId(doc.getTrip() != null ? doc.getTrip().id : null)
                    .title(doc.getTitle())
                    .contentType(doc.getContentType())
                    .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                    .visibility(doc.getVisibility() != null ? doc.getVisibility().name() : null)
                    .createdAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
                    .build();
            return Response.status(Response.Status.CREATED).entity(resp).build();
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ApiErrorBody.builder().code("STORAGE_UNAVAILABLE").message(e.getMessage()).build())
                    .build();
        } catch (IOException e) {
            return badRequest("Could not read uploaded file");
        } catch (Exception e) {
            log.error("Public passenger document upload failed", e);
            return serverError();
        }
    }

    private static String firstFormValue(Map<String, List<InputPart>> form, String key) {
        List<InputPart> parts = form.get(key);
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        try {
            String v = parts.getFirst().getBodyAsString();
            return v != null && !v.isBlank() ? v.trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractFileName(InputPart part) {
        if (part == null || part.getHeaders() == null) {
            return "document";
        }
        String cd = part.getHeaders().getFirst("Content-Disposition");
        if (cd == null) {
            return "document";
        }
        for (String token : cd.split(";")) {
            String t = token.trim();
            if (t.startsWith("filename=")) {
                String name = t.substring("filename=".length()).trim();
                if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
                    name = name.substring(1, name.length() - 1);
                }
                return name.isBlank() ? "document" : name;
            }
        }
        return "document";
    }

    private Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ApiErrorBody.builder().code("NOT_FOUND").message(message).build())
                .build();
    }

    private Response forbidden(String message) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ApiErrorBody.builder().code("FORBIDDEN").message(message).build())
                .build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiErrorBody.builder().code("BAD_REQUEST").message(message).build())
                .build();
    }

    private Response serverError() {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiErrorBody.builder().code("INTERNAL_ERROR").message("Internal error").build())
                .build();
    }
}
