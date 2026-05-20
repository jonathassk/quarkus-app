package org.example.application.dto.user.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import org.example.domain.enums.Gender;
import org.example.utils.GenderJsonbAdapter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequestDTO {
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
    @JsonbTypeAdapter(GenderJsonbAdapter.class)
    private Gender gender;
    private String timezone;
    private String bio;
}
