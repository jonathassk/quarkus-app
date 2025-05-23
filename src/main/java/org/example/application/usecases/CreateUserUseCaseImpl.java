package org.example.application.usecases;

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

    }

    @Override
    public void createUserEmail(String name, String email, String password) {

    }
}