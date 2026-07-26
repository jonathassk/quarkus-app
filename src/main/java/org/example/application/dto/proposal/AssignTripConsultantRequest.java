package org.example.application.dto.proposal;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTripConsultantRequest {
    /** null remove a atribuição. */
    private UUID consultantId;
}
