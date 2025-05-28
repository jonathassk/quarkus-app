package org.example.application.services;

import org.example.application.dto.user.response.UserResponseDTO;
import org.example.domain.entity.User;

public interface UserService {
    void validatePassword(String password);
    void validateEmail(String email);
    String encryptPassword(String password);
    String validateUser(User user, String Password);
    UserResponseDTO mapperResponseUser(User user, String token);
}
