package org.example.application.dto.user.response;

import java.util.UUID;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserResponseDTO {
    private String token;
    private String refreshToken;
    private Long expiresIn;
    private String username;
    private UUID id;
    private String email;
    private String fullname;
    private String userType;
    private String avatar;
}
