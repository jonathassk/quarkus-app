package org.example.application.usecases;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.application.dto.user.request.UserCreateRequestDTO;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.application.services.UserService;
import org.example.application.services.impl.UserSyncService;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.modelmapper.ModelMapper;

@Slf4j
@Transactional
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserSyncService userSyncService;

    @Override
    public void createUserFacebookAccount(String name, String email) {

    }

    @Override
    public void createUserGoogleAccount(String name, String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required for Google account");
        }
        userSyncService.resolveOrCreateGoogleByEmail(email, name);
        log.info("Google account ensured in users table for email={}", email.trim().toLowerCase());
    }

    @Override
    @Transactional
    public UserResponseDTO createUserEmail(UserCreateRequestDTO userRequest) {
        try {
            userService.validatePassword(userRequest.getPassword());
            userService.validateEmail(userRequest.getEmail());
            if (userRepository.findByEmailOrUsername(userRequest.getEmail(), userRequest.getUsername()).isPresent()) {
                log.warn("Create user failed: email or username already exists, email={}", userRequest.getEmail());
                throw new IllegalArgumentException("Email or username already exists.");
            }
            String encryptedPassword = userService.encryptPassword(userRequest.getPassword());
            ModelMapper mapper = ModelMapperFactory.createModelMapper();
            User user = mapper.map(userRequest, User.class);
            user.setPasswordHash(encryptedPassword);
            userRepository.persist(user);
            UserResponseDTO response = mapper.map(user, UserResponseDTO.class);
            response.setToken(userService.validateUser(user, userRequest.getPassword()));
            response.setExpiresIn(18000L);
            response.setId(user.id);
            return response;
        } catch (IllegalArgumentException e) {
            log.warn("Create user validation failed: email={}, reason={}", userRequest.getEmail(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Create user failed: email={}", userRequest.getEmail(), e);
            throw e;
        }
    }

    private UserResponseDTO mapperResponse(User user) {
        return null;
    }
}
