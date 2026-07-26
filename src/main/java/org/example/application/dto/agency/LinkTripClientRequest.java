package org.example.application.dto.agency;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkTripClientRequest {
    private UUID clientId;
}
