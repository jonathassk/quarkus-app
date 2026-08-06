package org.example.application.dto.ops;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LinkPassengersRequest {
    private List<UUID> passengerIds;
}
