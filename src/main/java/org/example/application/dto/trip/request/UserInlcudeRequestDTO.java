package org.example.application.dto.trip.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserInlcudeRequestDTO {
    private Long userId;
    private String permissionLevel;
}
