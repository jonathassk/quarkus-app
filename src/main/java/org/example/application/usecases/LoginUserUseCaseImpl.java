package org.example.application.usecases;

import io.quarkus.security.UnauthorizedException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.TokenService;
import org.example.application.services.UserService;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;

import java.util.Optional;

@Slf4j
@Transactional
@RequiredArgsConstructor
public class LoginUserUseCaseImpl implements LoginUserUseCase {

    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    @Override
    public UserResponseDTO LoginUserEmailUsername(String email, String password) {
        Optional<User> user = userRepository.findByUsernameOrEmail(email);
        if (user.isEmpty()) {
            log.warn("Login failed: user not found, email={}", email);
            throw new UnauthorizedException("Invalid username");
        }
        try {
            String token = userService.validateUser(user.get(), password);
            String refreshToken = tokenService.generateRefreshToken(user.get());
            return UserResponseDTO.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .email(user.get().getEmail())
                    .username(user.get().getUsername())
                    .fullname(user.get().getFullName())
                    .id(user.get().id)
                    .expiresIn(604800L)
                    .build();
        } catch (UnauthorizedException e) {
            log.warn("Login failed: invalid password, email={}", email);
            throw e;
        } catch (Exception e) {
            log.error("Login failed: email={}", email, e);
            throw new RuntimeException("Error generating refresh token", e);
        }
    }

    @Override
    public void LoginUserGoogle() {
        // Login Google é feito no Neon Auth; o front chama POST /api/v1/auth/session-sync com o JWT.
        log.debug("LoginUserGoogle: use Neon Auth + POST /api/v1/auth/session-sync");
    }

    @Override
    public void LoginUserFacebook() {

    }
}
