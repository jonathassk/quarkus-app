package org.example.application.dto.document;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadDocumentResponse {
    private UUID documentId;
    private String uploadUrl;
    private String s3Key;
    private int expiresInSeconds;
}
