package org.example.application.dto.user.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequestDTO {
    private String fullName;
    private String language;
    private String preferredLanguage;
}
