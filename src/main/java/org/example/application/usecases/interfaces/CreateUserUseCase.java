package org.example.application.usecases.interfaces;

import org.example.application.dto.UserRequestDTO;

public interface CreateUserUseCase {
    void createUserFacebookAccount(String name, String email);
    void createUserGoogleAccount(String name, String email);
    void createUserEmail(UserRequestDTO userRequest);
}
