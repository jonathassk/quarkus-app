package org.example.application.dto.ops;

import lombok.Data;
import org.example.domain.enums.SupplierCategory;

@Data
public class UpsertAgencySupplierRequest {
    private String name;
    private SupplierCategory category;
    private String contactName;
    private String email;
    private String whatsapp;
    private String website;
    private String currencies;
    private String notes;
    private String defaultPolicy;
}
