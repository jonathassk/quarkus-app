package org.example.application.dto.checklist;

import lombok.Data;

@Data
public class CreateTripChecklistItemRequest {
    private String title;
    private String notes;
}
