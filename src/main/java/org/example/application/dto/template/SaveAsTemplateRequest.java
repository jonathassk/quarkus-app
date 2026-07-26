package org.example.application.dto.template;

import lombok.*;
import org.example.domain.enums.TripTemplateKind;
import org.example.domain.enums.TripTemplateScope;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAsTemplateRequest {
    private String name;
    private String description;
    /** PERSONAL (default) ou AGENCY. */
    private TripTemplateScope scope;
    /** FULL_TRIP (default) ou SEGMENT_BLOCK. */
    private TripTemplateKind kind;
    /** Obrigatório quando kind = SEGMENT_BLOCK. */
    private java.util.UUID segmentId;
}
