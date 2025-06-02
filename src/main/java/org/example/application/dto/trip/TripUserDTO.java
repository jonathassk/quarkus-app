package org.example.application.dto.trip;

import lombok.*;
import org.example.domain.enums.UserPermissionLevel;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUserDTO {
    private Long userId;
    private UserPermissionLevel permissionLevel;
}