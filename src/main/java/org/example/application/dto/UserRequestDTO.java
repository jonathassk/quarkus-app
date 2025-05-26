package org.example.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.Gender;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {
    private String username;
    private String email;
    private String password;
    private String fullname;
    private String city;
    private String country;
    private String pictureUrl;
    private String dateOfBirth;
    private String language;
    private String phoneNumber;
    private Gender gender;
    private String timezone;
    private String bio;
}
