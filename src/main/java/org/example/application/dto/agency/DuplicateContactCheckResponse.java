package org.example.application.dto.agency;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateContactCheckResponse {
    private boolean hasMatches;
    private List<Match> matches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Match {
        private UUID clientId;
        private String name;
        private String email;
        private String phone;
        private String contactStatus;
        private long opportunityCount;
    }
}
