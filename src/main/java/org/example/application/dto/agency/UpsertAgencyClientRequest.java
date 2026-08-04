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

    private String birthPlace;
    private String nationality;
    private String documentNumber;
    private String documentType;
    /** ISO date yyyy-MM-dd */
    private String documentIssuedAt;
    /** ISO date yyyy-MM-dd */
    private String documentExpiresAt;
    /** ISO date yyyy-MM-dd */
    private String birthDate;
    private String gender;
}
