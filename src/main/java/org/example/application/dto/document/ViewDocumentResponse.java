package org.example.application.dto.document;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViewDocumentResponse {
    private Long documentId;
    private String viewUrl;
    private String contentType;
    private String title;
    private int expiresInSeconds;
}
