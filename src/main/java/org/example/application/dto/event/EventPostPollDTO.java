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
public class EventPostPollDTO {
    private List<EventPostPollOptionDTO> options;
    private long totalVotes;
    /** Index of the authenticated user's vote, or null if they have not voted. */
    private Integer myVoteIndex;
}
