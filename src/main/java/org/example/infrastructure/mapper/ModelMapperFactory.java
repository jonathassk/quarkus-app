package org.example.infrastructure.mapper;

import org.example.application.dto.user.request.UserCreateRequestDTO;
import org.example.application.dto.user.response.UserResponseDTO;
import org.example.domain.entity.User;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.convention.MatchingStrategies;

import java.time.Instant;

public class ModelMapperFactory {

    public static ModelMapper createModelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);

        // Custom mapping for User
        modelMapper.addMappings(new PropertyMap<UserCreateRequestDTO, User>() {
            @Override
            protected void configure() {
                map().setFullName(source.getFullname());
                map().setProfilePictureUrl(source.getPictureUrl());
                map().setPreferredLanguage(source.getLanguage());
                map().setPhoneNumber(source.getPhoneNumber());
                map().setTimezone(source.getTimezone());
                map().setBio(source.getBio());
                skip().setCreatedAt(Instant.now());
                skip().setUpdatedAt(Instant.now());
                skip().setLastLoginAt(Instant.now());
                skip().setDeletedAt(null);
                skip().setPhoneVerified(null);
            }
        });

        return modelMapper;
    }

    public static UserResponseDTO mapperResponseUser(User user, String token) {
        ModelMapper modelMapper = new ModelMapper();
        return null;
    }
} 