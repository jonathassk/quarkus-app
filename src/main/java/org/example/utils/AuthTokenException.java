package org.example.utils;

import lombok.Getter;

/** Erro de autenticação com código estável para o front (refresh vs logout). */
@Getter
public class AuthTokenException extends RuntimeException {

    private final String code;

    public AuthTokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AuthTokenException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
