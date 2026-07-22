package org.example.application.dto.document;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TripDocumentResponse {
    private UUID id;
    private UUID tripId;
    private String title;
    private String contentType;
    private String status;
    private String visibility;
    private UUID activityId;
    private UUID segmentId;
    private String createdAt;
}
