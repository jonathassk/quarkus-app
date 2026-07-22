package org.example.application.dto.documentexpiry;

import lombok.Data;

/** Corpo do PATCH — todos os campos são opcionais (atualização parcial). */
@Data
public class UpdateDocumentExpiryRequest {
    private String name;
    private String expiryDate;
    private Boolean alertEnabled;
}
