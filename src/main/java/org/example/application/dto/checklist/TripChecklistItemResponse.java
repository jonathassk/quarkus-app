package org.example.application.dto.checklist;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripChecklistItemResponse {
    private UUID id;
    private UUID tripId;
    private String title;
    private String notes;
    private Boolean completed;
    private Integer sortOrder;
    private String createdAt;
    private String updatedAt;
}
