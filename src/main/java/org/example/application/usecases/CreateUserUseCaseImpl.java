package org.example.application.usecases;

import org.example.application.dto.UserRequestDTO;
import org.example.application.usecases.interfaces.CreateUserUseCase;

public class CreateUserUseCaseImpl implements CreateUserUseCase {
    public String getTestMessage() {
        return "Mensagem de teste do Service";
    }

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

    }
}