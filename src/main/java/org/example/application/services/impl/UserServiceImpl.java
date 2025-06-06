package org.example.application.services.impl;

import io.quarkus.security.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.TokenService;
import org.example.application.services.UserService;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;

    @Override
    public void validatePassword(String password) {
        if (!password.matches(".*[A-Z].*")) throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        if (!password.matches(".*[a-z].*")) throw new IllegalArgumentException("Password must contain at least one lowercase letter.");
        if (!password.matches(".*[0-9].*")) throw new IllegalArgumentException("Password must contain at least one number.");
        if (!password.matches(".*[!@#$%^&*(),.?\":{}|<>].*"))throw new IllegalArgumentException("Password must contain at least one special character.");
    }

    @Override
    public void validateEmail(String email) {
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) throw new IllegalArgumentException("Invalid email format.");
        if (email.length() < 6) throw new IllegalArgumentException("Email must be at least 5 characters long.");
        if (email.length() > 60) throw new IllegalArgumentException("Email must be at most 60 characters long.");
        if (userRepository.findByEmail(email).isPresent()) throw new IllegalArgumentException("Email already exists.");
    }

    @Override
    public String encryptPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    @Override
    public String validateUser(User user, String password) {
        if (!BCrypt.checkpw(password, user.getPasswordHash())) throw new UnauthorizedException("Invalid password");
        try {
            return tokenService.generateToken(user, password);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public UserResponseDTO mapperResponseUser(User user, String token) {
        return null;
    }
}
