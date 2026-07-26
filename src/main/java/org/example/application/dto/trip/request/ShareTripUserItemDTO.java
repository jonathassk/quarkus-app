package org.example.application.dto.trip.request;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTripUserItemDTO {
    private UUID userId;
    private String email;
    @org.eclipse.microprofile.openapi.annotations.media.Schema(
            description = "Nível de permissão do colaborador (OWNER é exclusivo do criador da viagem).",
            enumeration = {"ADMIN", "VIEWER"},
            defaultValue = "VIEWER")
    private String permission;
}
