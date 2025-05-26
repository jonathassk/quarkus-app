package org.example.application.usecases;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.application.dto.UserRequestDTO;
import org.example.application.services.UserService;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.modelmapper.ModelMapper;

@Transactional
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private final UserService userService;
    private final UserRepository userRepository;

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
    @Transactional
    public void createUserEmail(UserRequestDTO userRequest) {
        userService.validatePassword(userRequest.getPassword());
        userService.validateEmail(userRequest.getEmail());
        if (userRepository.findByEmailOrUsername(userRequest.getEmail(), userRequest.getUsername()).isPresent())throw new IllegalArgumentException("Email or username already exists.");
        String encryptedPassword = userService.encryptPassword(userRequest.getPassword());
        ModelMapper mapper = ModelMapperFactory.createModelMapper();
        User user = mapper.map(userRequest, User.class);
        user.setPasswordHash(encryptedPassword);
        userRepository.persist(user);
    }
}