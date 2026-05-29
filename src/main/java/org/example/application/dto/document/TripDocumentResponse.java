package org.example.application.dto.document;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripDocumentResponse {
    private Long id;
    private Long tripId;
    private String title;
    private String contentType;
    private String status;
    private String createdAt;
}
