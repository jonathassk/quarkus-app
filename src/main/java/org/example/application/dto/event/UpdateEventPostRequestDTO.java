package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventPostRequestDTO {
    /** When set, pins or unpins the post (organizer only). */
    private Boolean pinned;
}
