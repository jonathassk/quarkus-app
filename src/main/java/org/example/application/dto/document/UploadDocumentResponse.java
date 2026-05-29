package org.example.application.dto.document;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadDocumentResponse {
    private Long documentId;
    private String uploadUrl;
    private String s3Key;
    private int expiresInSeconds;
}
