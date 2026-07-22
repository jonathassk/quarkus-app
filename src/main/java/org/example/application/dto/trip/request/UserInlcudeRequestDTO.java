package org.example.application.dto.trip.request;

import java.util.UUID;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserInlcudeRequestDTO {
    private UUID userId;
    private String permissionLevel;
}
