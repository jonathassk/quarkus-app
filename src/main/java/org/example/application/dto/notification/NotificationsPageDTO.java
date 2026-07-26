package org.example.application.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationsPageDTO {
    private List<NotificationDTO> items;
    private long total;
    private int page;
    private int size;
    private boolean hasMore;
    private long unreadCount;
}
