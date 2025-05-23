package org.example.adapters.rest;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.UserRequestDTO;
import org.example.domain.entity.User;
import org.modelmapper.ModelMapper;

@RequiredArgsConstructor
public class UserControllerAdapter {
    
    private final ModelMapper modelMapper;

    public User toEntity(UserRequestDTO dto) {
        User user = modelMapper.map(dto, User.class);
        user.setAccountStatus("active");
        user.setEmailVerified(false);
        return user;
    }
} 