package org.example.application.dto.document;

import lombok.Data;

@Data
public class UploadDocumentRequest {
    /** Original file name, e.g. passaporte.pdf */
    private String fileName;
    /** MIME type, e.g. application/pdf */
    private String contentType;
    /** Optional display title; defaults to fileName */
    private String title;
    /** CLIENT (default) or INTERNAL — agency vouchers. */
    private String visibility;
    private java.util.UUID activityId;
    private java.util.UUID segmentId;
}
