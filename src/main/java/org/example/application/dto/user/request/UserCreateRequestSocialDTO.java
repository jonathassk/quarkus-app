package org.example.application.dto.user.request;

public record UserCreateRequestSocialDTO(
        String email,
        String fullName,
        String provider,
        String providerAccessToken,
        String providerAvatarUrl,
        String language,
        String timezone
) {}