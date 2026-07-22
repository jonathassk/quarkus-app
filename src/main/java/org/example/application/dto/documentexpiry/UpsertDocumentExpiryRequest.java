package org.example.application.dto.documentexpiry;

import lombok.Data;

/**
 * Corpo do POST de criação/upsert. Para tipos fixos (PASSPORT, VISA,
 * INTERNATIONAL_LICENSE) o registro existente do usuário é atualizado em vez
 * de duplicado. Para CUSTOM, sempre cria um novo documento.
 */
@Data
public class UpsertDocumentExpiryRequest {
    /** PASSPORT | VISA | INTERNATIONAL_LICENSE | CUSTOM (padrão: CUSTOM). */
    private String kind;
    /** Obrigatório quando kind = CUSTOM. */
    private String name;
    /** Data ISO-8601 (yyyy-MM-dd), ou nulo/vazio para limpar. */
    private String expiryDate;
    private Boolean alertEnabled;
}
