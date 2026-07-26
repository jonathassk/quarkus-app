package org.example.application.dto.document;

import java.util.UUID;

import lombok.Data;

@Data
public class ConfirmUploadRequest {
    private UUID documentId;
    /** Tamanho real após o PUT no R2 (opcional, atualiza a quota). */
    private Long sizeBytes;
}
