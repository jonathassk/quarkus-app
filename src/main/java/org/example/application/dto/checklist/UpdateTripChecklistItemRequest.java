package org.example.application.dto.checklist;

import lombok.Data;

@Data
public class UpdateTripChecklistItemRequest {
    private String title;
    private String notes;
    private Boolean completed;
}
