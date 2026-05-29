package org.example.application.dto.checklist;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripChecklistItemResponse {
    private Long id;
    private Long tripId;
    private String title;
    private String notes;
    private Boolean completed;
    private Integer sortOrder;
    private String createdAt;
    private String updatedAt;
}
