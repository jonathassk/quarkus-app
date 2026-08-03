package org.example.application.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventPostRequestDTO {
    private String text;
    private String imageUrl;
    private String location;
    /** Optional poll choices (2–6). When set, the post text is the question. */
    private List<String> pollOptions;
}
