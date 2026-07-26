package org.example.application.dto.agency;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertAgencyClientRequest {
    private String name;
    private String email;
    private String phone;
    private String notes;
    /** Tags separadas por vírgula ou lista no JSON — o service aceita string. */
    private String tags;
}
