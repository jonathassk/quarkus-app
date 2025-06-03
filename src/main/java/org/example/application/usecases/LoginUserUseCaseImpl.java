package org.example.application.usecases;

import io.quarkus.security.UnauthorizedException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.UserService;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;

import java.util.Optional;

@Transactional
@RequiredArgsConstructor
public class LoginUserUseCaseImpl implements LoginUserUseCase {

    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    public UserResponseDTO LoginUserEmailUsername(String email, String password) {
        Optional<User> user = userRepository.findByUsernameOrEmail(email);
        if (user.isEmpty()) throw new UnauthorizedException("Invalid username");
        String token = userService.validateUser(user.get(), password);
        return UserResponseDTO.builder()
                .token(token)
                .email(user.get().getEmail())
                .username(user.get().getUsername())
                .fullname(user.get().getFullName())
                .id(user.get().id)
                .expiresIn(18000L)
                .build();
    }

    @Override
    public void LoginUserGoogle() {

    }

    @Override
    public void LoginUserFacebook() {

    }
}
