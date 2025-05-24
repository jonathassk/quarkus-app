package org.example.application.dto;

public record UserRequestSocialDto(
        String email,
        String fullName,
        String provider,
        String providerAccessToken,
        String providerAvatarUrl,
        String language,
        String timezone
) {}