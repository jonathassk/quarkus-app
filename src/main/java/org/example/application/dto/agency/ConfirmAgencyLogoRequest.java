package org.example.application.dto.agency;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmAgencyLogoRequest {
    private String s3Key;
    private String publicUrl;
}
