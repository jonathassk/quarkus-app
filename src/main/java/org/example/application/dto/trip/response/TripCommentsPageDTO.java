package org.example.application.dto.trip.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCommentsPageDTO {
    private List<TripCommentDTO> items;
    private long unreadCount;
}
