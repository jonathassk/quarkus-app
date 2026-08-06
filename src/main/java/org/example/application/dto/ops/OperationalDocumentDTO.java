package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.OperationalDocumentKind;
import org.example.domain.enums.OperationalDocumentStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalDocumentDTO {
    private UUID id;
    private UUID tripId;
    private UUID serviceId;
    private String serviceName;
    private String title;
    private String contentType;
    private OperationalDocumentKind documentKind;
    private OperationalDocumentStatus operationalDocStatus;
    private String visibility;
    private String createdAt;
}
