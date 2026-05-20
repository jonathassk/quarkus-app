package org.example.application.dto.user.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SyncUserRequest {
    private String cognitoSub;
    private String email;
    private String fullName;
    private String pictureUrl;
    /** "cognito", "google", "facebook" */
    private String provider;
}
