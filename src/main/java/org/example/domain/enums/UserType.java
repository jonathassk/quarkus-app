package org.example.domain.enums;

import lombok.Getter;

/**
 * Tipo de usuário na plataforma Baggagi.
 *
 * <ul>
 *   <li>{@link #GUEST}   – criado por uma agência de viagens; autentica via Magic Link JWT,
 *                          sem credenciais registradas no Neon Auth.</li>
 *   <li>{@link #FREE}    – usuário B2C com conta gratuita no Neon Auth.</li>
 *   <li>{@link #PREMIUM} – usuário B2C com assinatura ativa (mensal ou anual).</li>
 * </ul>
 */
@Getter
public enum UserType {
    GUEST("guest"),
    FREE("free"),
    PREMIUM("premium");

    private final String description;

    UserType(String description) {
        this.description = description;
    }

    public static UserType fromString(String value) {
        if (value == null || value.isBlank()) {
            return FREE;
        }
        for (UserType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de usuário inválido: " + value);
    }
}
