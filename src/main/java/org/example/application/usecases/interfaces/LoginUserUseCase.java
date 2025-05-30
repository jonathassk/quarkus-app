package org.example.application.usecases.interfaces;

import org.example.application.dto.user.response.UserResponseDTO;

public interface LoginUserUseCase {
    UserResponseDTO LoginUserEmailUsername(String email, String password);
    void LoginUserGoogle();
    void LoginUserFacebook();
}
