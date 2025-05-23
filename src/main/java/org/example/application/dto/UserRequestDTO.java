package org.example.application.dto;

import org.example.domain.enums.Gender;

public record UserRequestDTO(String username,
                             String email,
                             String password,
                             String fullname,
                             String city,
                             String country,
                             String pictureUrl,
                             String dateOfBirth,
                             String language,
                             String phoneNumber,
                             String timezone,
                             String bio) {}
