package org.example.application.usecases;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.UserRequestDTO;
import org.example.application.services.UserValidationService;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.domain.repository.UserRepository;

@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private final UserValidationService userValidationService;
    private final UserRepository userRepository;

    @Override
    public void createUserFacebookAccount(String name, String email) {

    }

    @Override
    public void createUserGoogleAccount(String name, String email) {
        //TODO: lembrar de tornar o email verified
        //TODO: password = null
        //TODO: verificar se o usuario ja existe no nosso sistema
    }

    @Override
    public void createUserEmail(UserRequestDTO userRequest) {
        userValidationService.validatePassword(userRequest.password());
        userValidationService.validateEmail(userRequest.email());
        if (userRepository.findByEmailOrUsername(userRequest.email(), userRequest.username()).isPresent())throw new IllegalArgumentException("Email or username already exists.");

    }
}