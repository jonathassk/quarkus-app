package org.example.application.dto.documentexpiry;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DocumentExpiryDTO {
    private UUID id;
    private String kind;
    private String name;
    private String expiryDate;
    private boolean alertEnabled;
}
