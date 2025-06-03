package org.example.application.services;

import org.example.domain.entity.User;
import java.io.IOException;
import java.security.GeneralSecurityException;

public interface TokenService {
    String generateToken(User user, String password) throws GeneralSecurityException, IOException;
    String validateToken(String token);
    String refreshToken(String token);
}
