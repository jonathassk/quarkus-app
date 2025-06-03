package org.example.application.dto.user.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserResponseDTO {
    private String token;
    private Long expiresIn;
    private String username;
    private Long id;
    private String email;
    private String fullname;
}
