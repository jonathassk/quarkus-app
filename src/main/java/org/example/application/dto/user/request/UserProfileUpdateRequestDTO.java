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
    private String phoneNumber;
    private String gender;
    private String bio;
    private String dateOfBirth;
    private String city;
    private String country;
    private String avatar;
}
