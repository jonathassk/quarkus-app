package org.example.application.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.domain.enums.SupplierCategory;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgencySupplierDTO {
    private UUID id;
    private String name;
    private SupplierCategory category;
    private String contactName;
    private String email;
    private String whatsapp;
    private String website;
    private String currencies;
    private String notes;
    private String defaultPolicy;
    private long servicesCount;
}
