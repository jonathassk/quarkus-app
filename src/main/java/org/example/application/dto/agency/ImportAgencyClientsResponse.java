package org.example.application.dto.agency;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportAgencyClientsResponse {
    private int created;
    private int updated;
    private List<UUID> clientIds;
    private List<String> errors;
}
