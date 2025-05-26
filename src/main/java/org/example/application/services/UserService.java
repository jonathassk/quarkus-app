package org.example.application.services;

import org.mindrot.jbcrypt.BCrypt;

public interface UserService {
    void validatePassword(String password);
    void validateEmail(String email);
    String encryptPassword(String password);
}
