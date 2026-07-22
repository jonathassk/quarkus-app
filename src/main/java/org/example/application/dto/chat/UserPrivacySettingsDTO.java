package org.example.application.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacySettingsDTO {
    private boolean publicProfile;
    private Boolean allowDmPublic;
    private Boolean allowDmFollowersOnly;
}
