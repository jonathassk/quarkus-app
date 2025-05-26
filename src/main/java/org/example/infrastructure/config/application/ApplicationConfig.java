package org.example.infrastructure.config.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.example.application.services.UserValidationService;
import org.example.application.services.impl.UserValidationServiceImpl;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.adapters.rest.UserControllerAdapter;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.controller.UserController;
import org.example.application.dto.UserRequestDTO;
import org.example.domain.entity.User;
import org.example.domain.repository.UserRepository;
import org.example.utils.UserDataVerification;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.PropertyMap;

@ApplicationScoped
public class ApplicationConfig {
    
    @Produces
    @ApplicationScoped
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);

        // Custom mapping for User
        modelMapper.addMappings(new PropertyMap<UserRequestDTO, User>() {
            @Override
            protected void configure() {
                map().setPasswordHash(source.password());
                map().setFullName(source.fullname());
                map().setProfilePictureUrl(source.pictureUrl());
                map().setPreferredLanguage(source.language());
                map().setPhoneNumber(source.phoneNumber());
                map().setTimezone(source.timezone());
                map().setBio(source.bio());
                skip().setCreatedAt(null);
                skip().setUpdatedAt(null);
                skip().setLastLoginAt(null);
                skip().setDeletedAt(null);
                skip().setPhoneVerified(null);
            }
        });

        return modelMapper;
    }

    @Produces
    @ApplicationScoped
    public UserDataVerification userDataVerification() {
        return new UserDataVerification();
    }

    @Produces
    @ApplicationScoped
    public UserValidationService userValidationService(UserRepository userRepository) {
        return new UserValidationServiceImpl(userRepository);
    }

    @Produces
    @ApplicationScoped
    public UserRepository userRepository() {
        return new UserRepository();
    }

    @Produces
    @ApplicationScoped
    public CreateUserUseCase createUserUseCase(
            UserValidationService userValidationService,
            UserRepository userRepository) {
        return new CreateUserUseCaseImpl(userValidationService, userRepository);
    }

    @Produces
    @ApplicationScoped
    public UserControllerAdapter userControllerAdapter(ModelMapper modelMapper) {
        return new UserControllerAdapter(modelMapper);
    }

    @Produces
    @ApplicationScoped
    public UserController userController(
            UserDataVerification userDataVerification,
            CreateUserUseCase createUserUseCase) {
        return new UserController(userDataVerification, createUserUseCase);
    }
} 