package org.example.domain.enums;

/**
 * Visibilidade de um documento/voucher da viagem.
 */
public enum DocumentVisibility {
    /** Visível para o cliente na proposta pública. */
    CLIENT,
    /** Somente membros da agência. */
    INTERNAL;

    public static DocumentVisibility fromString(String value) {
        if (value == null || value.isBlank()) {
            return CLIENT;
        }
        for (DocumentVisibility v : values()) {
            if (v.name().equalsIgnoreCase(value.trim())) {
                return v;
            }
        }
        throw new IllegalArgumentException("document visibility inválida: " + value);
    }
}
