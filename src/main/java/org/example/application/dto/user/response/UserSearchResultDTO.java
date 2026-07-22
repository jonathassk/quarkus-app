package org.example.application.dto.user.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResultDTO {
    private UUID id;
    private String email;
    private String fullName;
    private String username;
}
