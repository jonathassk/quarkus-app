package org.example.application.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.NotificationKind;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private UUID id;
    private NotificationKind kind;
    private String title;
    private String body;
    private String entityType;
    private UUID entityId;
    /** Deep link relativo derivado de kind/entityType/entityId. */
    private String deepLink;
    private Instant readAt;
    private Instant createdAt;
}
