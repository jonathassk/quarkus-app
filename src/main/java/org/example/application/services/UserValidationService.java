package org.example.application.services;

public interface UserValidationService {
    void validatePassword(String password);
    void validateEmail(String email);
    void encryptPassword(String password);
}
