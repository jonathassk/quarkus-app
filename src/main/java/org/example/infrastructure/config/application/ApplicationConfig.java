package org.example.infrastructure.config.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.example.application.services.UserService;
import org.example.application.services.impl.UserServiceImpl;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.adapters.rest.UserControllerAdapter;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.controller.UserController;
import org.example.domain.repository.UserRepository;
import org.example.infrastructure.mapper.ModelMapperFactory;
import org.example.utils.UserDataVerification;

@ApplicationScoped
public class ApplicationConfig {
    
    @Produces
    @ApplicationScoped
    public UserDataVerification userDataVerification() {
        return new UserDataVerification();
    }

    @Produces
    @ApplicationScoped
    public UserService userValidationService(UserRepository userRepository) {
        return new UserServiceImpl(userRepository);
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
    public UserControllerAdapter userControllerAdapter() {
        return new UserControllerAdapter(ModelMapperFactory.createModelMapper());
    }

    @Produces
    @ApplicationScoped
    public UserController userController(UserDataVerification userDataVerification, CreateUserUseCase createUserUseCase) {
        return new UserController(userDataVerification, createUserUseCase);
    }
} 