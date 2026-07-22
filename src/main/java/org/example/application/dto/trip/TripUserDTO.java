package org.example.application.dto.trip;

import java.util.UUID;

import lombok.*;
import org.example.domain.enums.UserPermissionLevel;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUserDTO {
    private UUID userId;
    private String email;
    private String fullName;
    private UserPermissionLevel permissionLevel;
}