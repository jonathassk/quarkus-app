package org.example.application.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preferências de canal. Campos {@link Boolean} (nullable) no PUT/PATCH permitem
 * omitir chaves sem zerar toggles existentes (Jackson não força {@code false}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailPreferencesDTO {
    private Boolean emailUpdates;
    private Boolean tripReminders;
    private Boolean documentExpiryAlerts;
    /** Persistência de notificações in-app. Default true. */
    private Boolean inAppNotifications;
    /** E-mails de atividade (comentários, chat, RSVP, propostas internas, etc.). Default true. */
    private Boolean activityEmails;
}
