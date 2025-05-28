package org.example.infrastructure.config.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.services.TokenService;
import org.example.application.services.UserService;
import org.example.application.services.impl.UserServiceImpl;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.application.usecases.LoginUserUseCaseImpl;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.controller.UserController;
import org.example.domain.repository.UserRepository;
import org.example.utils.UserDataVerification;

@ApplicationScoped
public class ApplicationConfig {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location")
    String privateKeyLocation;
    
    @Produces
    @ApplicationScoped
    public UserDataVerification userDataVerification() {
        return new UserDataVerification();
    }

    @Produces
    @ApplicationScoped
    public UserService userValidationService(
            UserRepository userRepository,
            TokenService tokenService) {
        return new UserServiceImpl(userRepository, tokenService);
    }

    @Produces
    @ApplicationScoped
    public UserRepository userRepository() {
        return new UserRepository();
    }

    @Produces
    @ApplicationScoped
    public CreateUserUseCase createUserUseCase(
            UserService userService,
            UserRepository userRepository) {
        return new CreateUserUseCaseImpl(userService, userRepository);
    }

    @Produces
    @ApplicationScoped
    public LoginUserUseCase loginUserUseCase(
            UserService userService,
            UserRepository userRepository) {
        return new LoginUserUseCaseImpl(userService, userRepository);
    }

    @Produces
    @ApplicationScoped
    public UserController userController(
            UserDataVerification userDataVerification,
            CreateUserUseCase createUserUseCase,
            LoginUserUseCase loginUserUseCase) {
        return new UserController(userDataVerification, createUserUseCase, loginUserUseCase);
    }
} 