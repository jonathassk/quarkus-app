package org.example.application.usecases.interfaces;

public interface CreateUserUseCase {
    void createUserFacebookAccount(String name, String email);
    void createUserGoogleAccount(String name, String email);
    void createUserEmail(String name, String email, String password);
}
