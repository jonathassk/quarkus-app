package org.example.application.dto.trip.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSharePermissionDTO {
    @org.eclipse.microprofile.openapi.annotations.media.Schema(
            description = "Novo nível de permissão do colaborador (OWNER é exclusivo do criador da viagem).",
            enumeration = {"ADMIN", "VIEWER"},
            required = true)
    private String permission;
}
