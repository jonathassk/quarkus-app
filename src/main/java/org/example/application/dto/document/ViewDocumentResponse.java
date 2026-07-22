package org.example.application.dto.document;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViewDocumentResponse {
    private UUID documentId;
    private String viewUrl;
    private String contentType;
    private String title;
    private int expiresInSeconds;
}
