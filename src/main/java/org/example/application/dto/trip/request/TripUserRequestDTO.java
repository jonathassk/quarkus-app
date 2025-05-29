package org.example.application.dto.trip.request;

import lombok.*;
import org.example.domain.enums.UserPermissionLevel;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUserRequestDTO {
    private Long userId;
    private UserPermissionLevel permissionLevel;
}