package org.example.application.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripUserRequestDTO {
    private Long userId;
    private String permissionLevel;
}