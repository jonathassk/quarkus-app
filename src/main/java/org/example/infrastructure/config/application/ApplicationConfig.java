package org.example.infrastructure.config.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.adapters.rest.UserControllerAdapter;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.controller.UserController;
import org.example.application.dto.UserRequestDTO;
import org.example.domain.entity.User;
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
                skip().setId(null);
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
    public CreateUserUseCase createUserUseCase() {
        return new CreateUserUseCaseImpl();
    }

    @Produces
    @ApplicationScoped
    public UserControllerAdapter userControllerAdapter(ModelMapper modelMapper) {
        return new UserControllerAdapter(modelMapper);
    }

    @Produces
    @ApplicationScoped
    public UserController userController(UserControllerAdapter adapter) {
        return new UserController(adapter);
    }
} 