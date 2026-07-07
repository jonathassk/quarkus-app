package org.example.application.dto.user.response;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String email;
    private String username;
    private String fullName;
    private String avatar;
    private String preferredLanguage;
    private Instant createdAt;
    private Instant updatedAt;
    private String phoneNumber;
    private String gender;
    private String bio;
    private String dateOfBirth;
    private String city;
    private String country;
    private java.util.List<String> visitedCountries;
}
