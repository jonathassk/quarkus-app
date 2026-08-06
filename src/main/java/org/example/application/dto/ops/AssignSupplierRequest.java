package org.example.application.dto.ops;

import lombok.Data;

import java.util.UUID;

@Data
public class AssignSupplierRequest {
    private UUID supplierId;
    private String supplierName;
}
