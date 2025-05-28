package org.example.application.usecases.interfaces;

import org.example.application.dto.user.request.UserCreateRequestDTO;
import org.example.application.dto.user.response.UserResponseDTO;

public interface CreateUserUseCase {
    void createUserFacebookAccount(String name, String email);
    void createUserGoogleAccount(String name, String email);
    UserResponseDTO createUserEmail(UserCreateRequestDTO userRequest);
}
