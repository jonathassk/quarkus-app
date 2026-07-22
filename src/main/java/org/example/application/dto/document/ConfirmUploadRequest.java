package org.example.application.dto.document;

import java.util.UUID;

import lombok.Data;

@Data
public class ConfirmUploadRequest {
    private UUID documentId;
}
