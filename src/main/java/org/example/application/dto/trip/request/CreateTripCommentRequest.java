package org.example.application.dto.trip.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.TripCommentTargetType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripCommentRequest {
    private TripCommentTargetType targetType;
    private String targetId;
    private String body;
}
