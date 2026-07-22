package org.example.application.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailPreferencesDTO {
    private boolean emailUpdates;
    private boolean tripReminders;
    private boolean documentExpiryAlerts;
}
