package org.example.application.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkNotificationsReadRequest {
    /** IDs específicos a marcar como lidas (somente do usuário autenticado). */
    private List<UUID> ids;
    /** Se true, marca todas as não lidas do usuário. */
    private boolean all;
}
